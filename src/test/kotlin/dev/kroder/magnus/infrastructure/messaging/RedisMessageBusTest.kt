package dev.kroder.magnus.infrastructure.messaging

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RedisMessageBusTest {
    private val jedisPool = mockk<JedisPool>()
    private val jedis = mockk<Jedis>(relaxed = true)

    @Test
    fun `should publish message`() {
        val latch = CountDownLatch(1)
        
        every { jedisPool.resource } returns jedis
        every { jedis.publish(any<String>(), any<String>()) } answers {
            latch.countDown()
            1L
        }
        every { jedis.close() } just Runs
        
        val bus = RedisMessageBus(jedisPool)

        bus.publish("test-channel", "hello world")

        // Wait for async coroutine with proper synchronization
        val completed = latch.await(2, TimeUnit.SECONDS)
        assertTrue(completed, "Publish should complete within timeout")

        verify { jedis.publish("test-channel", "hello world") }
    }
}
