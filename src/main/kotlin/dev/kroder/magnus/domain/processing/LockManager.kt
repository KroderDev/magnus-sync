package dev.kroder.magnus.domain.processing

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.util.UUID

/**
 * Manages distributed locks for player sessions.
 * Prevents a player from joining a server if their data is still being saved on another.
 */
class LockManager(private val jedisPool: JedisPool) {
    private val logger = LoggerFactory.getLogger("magnus-lock")
    private val LOCK_PREFIX = "magnus:lock:"
    private val LOCK_TTL_SECONDS = 30L // Safety net in case of crash

    /**
     * Attempts to acquire a lock for the given player.
     * @return true if the lock *ALREADY EXISTS* (i.e., player is locked out).
     * @return false if the lock is free (safe to join).
     */
    fun isLocked(playerUuid: UUID): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.exists("$LOCK_PREFIX$playerUuid")
            }
        } catch (e: Exception) {
            logger.warn("Failed to check lock for $playerUuid: ${e.message}")
            false // Fail open to avoid blocking players if Redis is down
        }
    }

    /**
     * Sets a lock for a player.
     * Should be called when a player disconnects.
     */
    fun lock(playerUuid: UUID) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.setex("$LOCK_PREFIX$playerUuid", LOCK_TTL_SECONDS, "LOCKED")
                logger.debug("Locked session for $playerUuid")
            }
        } catch (e: Exception) {
            logger.error("Failed to set lock for $playerUuid: ${e.message}")
        }
    }

    /**
     * Releases the lock for a player.
     * Should be called when data save is complete.
     */
    fun unlock(playerUuid: UUID) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.del("$LOCK_PREFIX$playerUuid")
                logger.debug("Unlocked session for $playerUuid")
            }
        } catch (e: Exception) {
            logger.error("Failed to unlock session for $playerUuid: ${e.message}")
        }
    }
}
