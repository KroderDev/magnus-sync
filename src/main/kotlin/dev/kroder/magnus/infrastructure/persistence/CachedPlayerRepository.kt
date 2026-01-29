package dev.kroder.magnus.infrastructure.persistence

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import java.util.UUID

/**
 * A composite implementation of [PlayerRepository] that coordinates between a cache (Redis)
 * and a persistent store (Postgres).
 * 
 * Functional Limits:
 * - Read Strategy: Cache-Aside (Check Redis, then Postgres, then update Redis).
 * - Write Strategy: Write-Through (Update Redis and Postgres simultaneously).
 */
class CachedPlayerRepository(
    private val cache: PlayerRepository,
    private val persistentStore: PlayerRepository
) : PlayerRepository {

    override fun save(data: PlayerData) {
        // Save to both for consistency
        cache.save(data)
        persistentStore.save(data)
    }

    override fun findByUuid(uuid: UUID): PlayerData? {
        // Try the cache first
        val cached = cache.findByUuid(uuid)
        if (cached != null) return cached

        // If not in cache, load from persistent store
        val persistent = persistentStore.findByUuid(uuid) ?: return null

        // Update cache for next time
        cache.save(persistent)
        
        return persistent
    }

    override fun deleteCache(uuid: UUID) {
        cache.deleteCache(uuid)
    }
}
