package dev.kroder.magnus.infrastructure.persistence.redis

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.util.UUID

/**
 * Implementation of [PlayerRepository] using Redis for high-speed caching.
 * This adapter is responsible for "hot" data that needs to be accessed instantly.
 */
class RedisPlayerRepository(
    private val pool: JedisPool,
    private val maxPayloadSize: Int = 65536, // Default 64KB
    private val keyPrefix: String = "magnus:player:"
) : PlayerRepository {

    private val logger = LoggerFactory.getLogger("magnus-redis-repo")
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(data: PlayerData) {
        val value = json.encodeToString(data)
        
        // Security check: Payload size limit (R-03)
        if (value.length > maxPayloadSize) {
            logger.warn("Rejecting oversized persistence snapshot for ${data.username} (${data.uuid}): ${value.length} bytes. Max allowed: $maxPayloadSize")
            return
        }

        try {
            pool.resource.use { jedis ->
                val key = "$keyPrefix${data.uuid}"
                jedis.set(key, value)
            }
        } catch (e: Exception) {
            // Rethrow so CachedPlayerRepository can handle failure/logging
            throw e
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

