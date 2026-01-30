package dev.kroder.magnus.infrastructure.messaging

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class RedisMessageBusTest {
    private val jedisPool = mockk<JedisPool>()
    private val jedis = mockk<Jedis>(relaxed = true)

    @Test
    fun `should publish message`() {
        every { jedisPool.resource } returns jedis
        val bus = RedisMessageBus(jedisPool)

        bus.publish("test-channel", "hello world")

        // Wait a bit for coroutine
        Thread.sleep(100)

        verify { jedis.publish("test-channel", "hello world") }
    }
}
