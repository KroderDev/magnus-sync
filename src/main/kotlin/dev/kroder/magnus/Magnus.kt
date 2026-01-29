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

/**
 * Main entry point for the Magnus Sync mod.
 * This class follows the "Wiring" or "Composition Root" pattern, connecting all layers.
 * 
 * Functional Limits:
 * - Initialization is synchronous; if DB is down, the server might hang or crash.
 * - This class should stay lean, only handling dependency injection and lifecycle.
 */
object Magnus : ModInitializer {
    private val logger = LoggerFactory.getLogger("magnus-sync")
    private val config = MagnusConfig() // In production, load this from a file

    override fun onInitialize() {
        logger.info("Initializing Magnus Sync - Hexagonal Architecture")

        // 1. Initialize Postgres (Infrastructure)
        val database = Database.connect(
            url = config.postgresUrl,
            driver = "org.postgresql.Driver",
            user = config.postgresUser,
            password = config.postgresPass
        )

        try {
            transaction(database) {
                SchemaUtils.create(PlayerDataTable)
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to PostgreSQL on boot. Server will start in RESILIENCE MODE (Offline).", e)
        }

        // 2. Initialize Redis (Infrastructure)
        val jedisPool = JedisPool(JedisPoolConfig(), config.redisHost, config.redisPort)

        // 3. Create Repositories (Infrastructure)
        val postgresRepo = PostgresPlayerRepository(database)
        val redisRepo = RedisPlayerRepository(jedisPool)
        val compositeRepo = CachedPlayerRepository(cache = redisRepo, persistentStore = postgresRepo)
        
        // Resilience Layer
        val backupsDir = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("magnus/backups").toFile()
        val localBackupRepo = dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository(backupsDir)
        val resilientRepo = dev.kroder.magnus.infrastructure.persistence.ResilientPlayerRepository(compositeRepo, localBackupRepo)

        // 4. Create Application Services
        val syncService = SyncService(resilientRepo) // Use the resilient one!
        
        val recoveryService = dev.kroder.magnus.application.BackupRecoveryService(localBackupRepo, compositeRepo)
        recoveryService.start()

        // 5. Initialize Listeners (Infrastructure / Driving Adapter)
        val listener = PlayerEventListener(syncService)
        listener.register()

        logger.info("Magnus Sync initialized successfully!")
    }
}