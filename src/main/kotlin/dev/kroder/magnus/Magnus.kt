package dev.kroder.magnus

import dev.kroder.magnus.application.SyncService
import dev.kroder.magnus.infrastructure.persistence.CachedPlayerRepository
import dev.kroder.magnus.infrastructure.persistence.postgres.PlayerDataTable
import dev.kroder.magnus.infrastructure.persistence.postgres.PostgresPlayerRepository
import dev.kroder.magnus.infrastructure.persistence.redis.RedisPlayerRepository
import net.fabricmc.api.ModInitializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import dev.kroder.magnus.login.LoginQueueHandler

/**
 * Main entry point for the Magnus Sync mod.
 * This class follows the "Wiring" or "Composition Root" pattern, connecting all layers.
 * 
 * Functional Limits:
 * - Initialization is synchronous; if DB is down, the server might hang or crash.
 * - This class should stay lean, only handling dependency injection and lifecycle.
 */
object Magnus : ModInitializer {
    internal val logger = LoggerFactory.getLogger("magnus")
    private val config = dev.kroder.magnus.infrastructure.config.ConfigLoader.load()
    
    // Exposed services for Mixins
    lateinit var syncService: SyncService
        private set

    override fun onInitialize() {
        logger.info("Initializing Magnus")

        // 1. Initialize Postgres (Infrastructure)
        logger.info("Persistence: Connecting to PostgreSQL...")
        val database = Database.connect(
            url = config.postgresUrl,
            driver = "org.postgresql.Driver",
            user = config.postgresUser,
            password = config.postgresPass
        )

        try {
            transaction(database) {
                // Creates table if missing, or adds missing columns if table exists.
                @Suppress("DEPRECATION")
                SchemaUtils.createMissingTablesAndColumns(PlayerDataTable)
            }
            logger.info("Persistence: PostgreSQL connected and schema verified. [OK]")
        } catch (e: Exception) {
            logger.warn("Persistence: PostgreSQL connection FAILED. Server starting in RESILIENCE MODE (Offline). Reason: ${e.message}")
        }

        // 2. Initialize Redis (Infrastructure)
        logger.info("Cache: Initializing Redis...")
        
        val jedisPool = dev.kroder.magnus.infrastructure.messaging.JedisPoolFactory.create(
            host = config.redisHost,
            port = config.redisPort,
            password = config.redisPass,
            useSsl = config.redisSsl
        )

        try {
            jedisPool.resource.use { jedis ->
                val ping = jedis.ping()
                if (ping == "PONG") {
                    logger.info("Cache: Redis connected successfully. [OK]")
                    if (config.redisSsl) {
                        logger.info("Cache: SSL/TLS encryption is ENABLED")
                    }
                } else {
                     logger.warn("Cache: Redis connection unstable? Ping response: $ping")
                }
            }
        } catch (e: Exception) {
            logger.warn("Cache: Redis connection FAILED. Performance will be degraded. Reason: ${e.message}")
        }

        // 3. Create Repositories (Infrastructure)
        val postgresRepo = PostgresPlayerRepository(database)
        val redisRepo = RedisPlayerRepository(jedisPool, maxPayloadSize = config.maxMessageSizeBytes)
        val compositeRepo = CachedPlayerRepository(cache = redisRepo, persistentStore = postgresRepo)

        
        // Resilience Layer
        val backupsDir = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("magnus/backups").toFile()
        val localBackupRepo = dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository(backupsDir)
        val resilientRepo = dev.kroder.magnus.infrastructure.persistence.ResilientPlayerRepository(compositeRepo, localBackupRepo)

        // 4. Create Application Services
        val lockManager = if (config.enableSessionLock) {
            dev.kroder.magnus.domain.processing.LockManager(jedisPool)
        } else {
            null
        }

        syncService = SyncService(resilientRepo, lockManager)
        
        val recoveryService = dev.kroder.magnus.application.BackupRecoveryService(localBackupRepo, compositeRepo)
        recoveryService.start()

        // 5. Initialize Modular System with Secure MessageBus
        val moduleManager = dev.kroder.magnus.infrastructure.module.ModuleManager()
        
        // Create message signer if signing is enabled
        val messageSigner = if (config.enableMessageSigning && !config.messageSigningSecret.isNullOrEmpty()) {
            logger.info("MessageBus: Message signing is ENABLED")
            dev.kroder.magnus.infrastructure.security.MessageSigner(config.messageSigningSecret)
        } else {
            if (config.enableMessageSigning) {
                logger.warn("MessageBus: enableMessageSigning is true but no messageSigningSecret provided! Signing disabled.")
            }
            null
        }
        
        val messageBus = dev.kroder.magnus.infrastructure.messaging.SecureRedisMessageBus(
            jedisPool = jedisPool,
            signer = messageSigner,
            maxPayloadSize = config.maxMessageSizeBytes,
            retryDelayMs = config.subscriptionRetryDelayMs,
            maxRetries = config.maxSubscriptionRetries
        )
        
        // Register modules
        // Register Inventory Sync module (enabled by default)
        if (config.enableInventorySync) {
            val inventorySyncModule = dev.kroder.magnus.infrastructure.module.inventorysync.InventorySyncModule(
                syncService = syncService
            )
            moduleManager.registerModule(inventorySyncModule)
            moduleManager.enableModule("inventory-sync")
        }
        
        if (config.enableGlobalChat) {
            val globalChatModule = dev.kroder.magnus.infrastructure.module.globalchat.GlobalChatModule(
                messageBus = messageBus,
                serverName = config.serverName
            )
            moduleManager.registerModule(globalChatModule)
            moduleManager.enableModule("global-chat")
        }
        
        if (config.enableGlobalPlayerList) {
            val globalPlayerListModule = dev.kroder.magnus.infrastructure.module.globalplayerlist.GlobalPlayerListModule(
                messageBus = messageBus,
                serverName = config.serverName
            )
            moduleManager.registerModule(globalPlayerListModule)
            moduleManager.enableModule("global-player-list")
        }

        // 7. Initialize Login Queue Handler (uses Fabric API instead of Mixin)
        if (config.enableSessionLock) {
            LoginQueueHandler.register()
        }

        // 8. Graceful Shutdown Hook
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register {
            logger.info("Magnus: Shutting down services...")
            try {
                recoveryService.shutdown()
                moduleManager.shutdown()
                messageBus.close()
                jedisPool.close()
                logger.info("Magnus: Services stopped cleanly.")
            } catch (e: Exception) {
                logger.error("Magnus: Error during shutdown!", e)
            }
        }

        logger.info("Magnus initialized successfully! [Ready]")
    }
}