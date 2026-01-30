package dev.kroder.magnus

import dev.kroder.magnus.application.SyncService
import dev.kroder.magnus.infrastructure.config.MagnusConfig
import dev.kroder.magnus.infrastructure.fabric.PlayerEventListener
import dev.kroder.magnus.infrastructure.persistence.CachedPlayerRepository
import dev.kroder.magnus.infrastructure.persistence.postgres.PlayerDataTable
import dev.kroder.magnus.infrastructure.persistence.postgres.PostgresPlayerRepository
import dev.kroder.magnus.infrastructure.persistence.redis.RedisPlayerRepository
import net.fabricmc.api.ModInitializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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
        
        val jedisPool = if (config.redisPass != null && config.redisPass.isNotEmpty()) {
            JedisPool(JedisPoolConfig(), config.redisHost, config.redisPort, 2000, config.redisPass)
        } else {
            JedisPool(JedisPoolConfig(), config.redisHost, config.redisPort)
        }

        try {
            jedisPool.resource.use { jedis ->
                val ping = jedis.ping()
                if (ping == "PONG") {
                    logger.info("Cache: Redis connected successfully. [OK]")
                } else {
                     logger.warn("Cache: Redis connection unstable? Ping response: $ping")
                }
            }
        } catch (e: Exception) {
            logger.warn("Cache: Redis connection FAILED. Performance will be degraded. Reason: ${e.message}")
        }

        // 3. Create Repositories (Infrastructure)
        val postgresRepo = PostgresPlayerRepository(database)
        val redisRepo = RedisPlayerRepository(jedisPool)
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

        // 5. Initialize Modular System
        val moduleManager = dev.kroder.magnus.infrastructure.module.ModuleManager()
        
        // TODO: Register modules when implemented
        // Example: if (config.enableGlobalChat) moduleManager.registerModule(GlobalChatModule(...))

        // Enable modules based on configuration
        if (config.enableGlobalPlayerList) moduleManager.enableModule("global-player-list")

        // 6. Initialize Listeners (Infrastructure / Driving Adapter)
        val listener = PlayerEventListener(syncService)
        listener.register()

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
                jedisPool.close()
                logger.info("Magnus: Services stopped cleanly.")
            } catch (e: Exception) {
                logger.error("Magnus: Error during shutdown!", e)
            }
        }

        logger.info("Magnus initialized successfully! [Ready]")
    }
}