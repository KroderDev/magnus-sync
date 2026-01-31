package dev.kroder.magnus.infrastructure.persistence

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A composite implementation of [PlayerRepository] that coordinates between a cache (Redis)
 * and a persistent store (Postgres).
 * 
 * Resilience:
 * - Cache operations are isolated. Failure in Redis will not block the Persistent Store.
 */
class CachedPlayerRepository(
    private val cache: PlayerRepository,
    private val persistentStore: PlayerRepository
) : PlayerRepository {

    private val logger = LoggerFactory.getLogger("magnus-cached-repo")

    override fun save(data: PlayerData) {
        // 1. Try to save to cache (Redis)
        try {
            cache.save(data)
        } catch (e: Exception) {
            logger.warn("Cache save failed for ${data.uuid}: ${e.message}")
        }

        // 2. Always save to persistent store (Postgres)
        persistentStore.save(data)
    }

    override fun findByUuid(uuid: UUID): PlayerData? {
        // 1. Try the cache first (Isolated)
        try {
            val cached = cache.findByUuid(uuid)
            if (cached != null) return cached
        } catch (e: Exception) {
            logger.warn("Cache load failed for $uuid: ${e.message}")
        }

        // 2. Load from persistent store
        val persistent = persistentStore.findByUuid(uuid) ?: return null

        // 3. Update cache for next time (Isolated)
        try {
            cache.save(persistent)
        } catch (e: Exception) {
            logger.debug("Failed to update cache after DB load for $uuid: ${e.message}")
        }
        
        return persistent
    }

    override fun deleteCache(uuid: UUID) {
        try {
            cache.deleteCache(uuid)
        } catch (e: Exception) {
            logger.warn("Failed to delete cache for $uuid: ${e.message}")
        }
    }
}

