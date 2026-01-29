package dev.kroder.magnus.application

import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Service responsible for scanning local backups and "recovering" them to the primary database.
 * Runs periodically to ensure data consistency after an outage.
 */
class BackupRecoveryService(
    private val localBackup: LocalBackupRepository,
    private val primaryRepo: PlayerRepository // This should be the raw/composite repo, not the resilient one (to avoid loops)
) {
    private val logger = LoggerFactory.getLogger("magnus-recovery")
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        logger.info("Starting Backup Recovery Service (Janitor)...")
        // Run every 5 minutes
        scheduler.scheduleAtFixedRate({
            try {
                processBackups()
            } catch (e: Exception) {
                logger.error("Error during recovery process", e)
            }
        }, 1, 5, TimeUnit.MINUTES)
    }

    fun shutdown() {
        scheduler.shutdown()
    }

    /**
     * Scans and attempts to restore/merge backups.
     */
    fun processBackups() {
        val backups = localBackup.findAllStartups()
        if (backups.isEmpty()) return

        logger.info("Janitor found ${backups.size} local backups to process.")

        for (backupData in backups) {
            try {
                // Check what DB has
                // NOTE: We assume primaryRepo.findByUuid() might throw if DB is STILL down.
                // If so, we catch and skip (wait for next cycle).
                val dbData = primaryRepo.findByUuid(backupData.uuid)

                if (dbData == null) {
                    // DB has nothing (or maybe valid new player?), but we have a backup.
                    // Recover it.
                    logger.info("Recovering ${backupData.username}: Not found in DB, pushing local backup.")
                    primaryRepo.save(backupData)
                    localBackup.deleteFile(backupData.uuid)
                } else {
                    // DB has data. Compare timestamps.
                    if (backupData.lastUpdated > dbData.lastUpdated) {
                        logger.info("Recovering ${backupData.username}: Local backup is NEWER (${backupData.lastUpdated} > ${dbData.lastUpdated}). Overwriting DB.")
                        primaryRepo.save(backupData)
                        localBackup.deleteFile(backupData.uuid)
                    } else {
                        logger.info("Discarding backup for ${backupData.username}: DB is newer or equal.")
                        localBackup.deleteFile(backupData.uuid) // Obsolete backup
                    }
                }

            } catch (e: Exception) {
                logger.warn("Skipping recovery for ${backupData.username}: DB might be down or error.", e)
            }
        }
    }
}
