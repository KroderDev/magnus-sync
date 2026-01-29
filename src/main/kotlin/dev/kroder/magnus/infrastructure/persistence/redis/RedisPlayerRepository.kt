package dev.kroder.magnus.infrastructure.persistence.redis

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import java.util.UUID

/**
 * Implementation of [PlayerRepository] using Redis for high-speed caching.
 * This adapter is responsible for "hot" data that needs to be accessed instantly.
 * 
 * Functional Limits:
 * - Data in Redis is transient; it will expire or be evicted.
 * - [PlayerData] is serialized to JSON before storage.
 */
class RedisPlayerRepository(
    private val pool: JedisPool,
    private val keyPrefix: String = "magnus:player:"
) : PlayerRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(data: PlayerData) {
        pool.resource.use { jedis ->
            val key = "$keyPrefix${data.uuid}"
            val value = json.encodeToString(data)
            jedis.set(key, value)
            // Optional: set expiration if needed, e.g., jedis.expire(key, 3600)
        }
    }

    override fun findByUuid(uuid: UUID): PlayerData? {
        pool.resource.use { jedis ->
            val key = "$keyPrefix$uuid"
            val value = jedis.get(key) ?: return null
            return json.decodeFromString<PlayerData>(value)
        }
    }

    override fun deleteCache(uuid: UUID) {
        pool.resource.use { jedis ->
            jedis.del("$keyPrefix$uuid")
        }
    }
}
