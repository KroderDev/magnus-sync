package dev.kroder.magnus.infrastructure.persistence

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.infrastructure.persistence.redis.RedisPlayerRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.util.*

class PersistenceResilienceTest {

    @Test
    fun `CachedPlayerRepository should continue to Postgres if Redis save fails`() {
        val cache = mockk<PlayerRepository>()
        val postgres = mockk<PlayerRepository>(relaxed = true)
        val composite = CachedPlayerRepository(cache, postgres)
        val data = createSampleData()

        every { cache.save(any()) } throws RuntimeException("Redis connection refused")

        assertDoesNotThrow {
            composite.save(data)
        }

        verify { postgres.save(data) }
    }

    @Test
    fun `CachedPlayerRepository should continue to Postgres if Redis load fails`() {
        val cache = mockk<PlayerRepository>()
        val postgres = mockk<PlayerRepository>()
        val composite = CachedPlayerRepository(cache, postgres)
        val uuid = UUID.randomUUID()
        val data = createSampleData(uuid)

        every { cache.findByUuid(uuid) } throws RuntimeException("Redis Down")
        every { postgres.findByUuid(uuid) } returns data

        val result = composite.findByUuid(uuid)

        assertEquals(data, result)
        verify { postgres.findByUuid(uuid) }
    }

    @Test
    fun `RedisPlayerRepository should reject oversized payloads`() {
        val pool = mockk<JedisPool>()
        val repo = RedisPlayerRepository(pool, maxPayloadSize = 100)
        
        // Create large username to exceed 100 bytes serialized
        val largeData = createSampleData(username = "A".repeat(200))

        repo.save(largeData)

        verify(exactly = 0) { pool.resource }
    }

    @Test
    fun `RedisPlayerRepository should rethrow Jedis exceptions`() {
        val pool = mockk<JedisPool>()
        val repo = RedisPlayerRepository(pool)
        val data = createSampleData()

        every { pool.resource } throws RuntimeException("Jedis Error")

        assertThrows(RuntimeException::class.java) {
            repo.save(data)
        }
    }

    private fun createSampleData(uuid: UUID = UUID.randomUUID(), username: String = "Test"): PlayerData {
        return PlayerData(
            uuid = uuid,
            username = username,
            health = 20f,
            foodLevel = 20,
            saturation = 5f,
            exhaustion = 0f,
            air = 300,
            score = 0,
            selectedSlot = 0,
            experienceLevel = 0,
            experienceProgress = 0f,
            inventoryNbt = "{}",
            enderChestNbt = "{}",
            activeEffectsNbt = "[]",
            lastUpdated = System.currentTimeMillis()
        )
    }
}

