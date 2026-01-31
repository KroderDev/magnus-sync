package dev.kroder.magnus.infrastructure.messaging

import dev.kroder.magnus.domain.model.ChatMessage
import dev.kroder.magnus.domain.model.PlayerEntry
import dev.kroder.magnus.domain.model.ServerPlayerInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.redis.testcontainers.RedisContainer

/**
 * Integration tests for GlobalChatModule using real Redis via Testcontainers.
 * These tests verify the full pub/sub flow without mocking Minecraft classes.
 */
@Testcontainers
class GlobalChatIntegrationTest {
    
    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")
    }
    
    private lateinit var jedisPool: JedisPool
    private lateinit var messageBus: RedisMessageBus
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        jedisPool = JedisPool(
            JedisPoolConfig(),
            redis.host,
            redis.firstMappedPort
        )
        messageBus = RedisMessageBus(jedisPool)
    }

    @AfterEach
    fun teardown() {
        messageBus.close()
        jedisPool.close()
    }

    @Test
    fun `should publish and receive ChatMessage via Redis`() {
        val latch = CountDownLatch(1)
        var receivedMessage: ChatMessage? = null
        
        // Subscribe first
        messageBus.subscribe("magnus:chat") { payload ->
            receivedMessage = json.decodeFromString<ChatMessage>(payload)
            latch.countDown()
        }
        
        // Wait for subscription to be ready
        Thread.sleep(500)
        
        // Publish a message
        val chatMessage = ChatMessage(
            serverName = "survival",
            playerUuid = "test-uuid",
            playerName = "TestPlayer",
            rawMessage = "Hello from integration test!"
        )
        messageBus.publish("magnus:chat", json.encodeToString(chatMessage))
        
        // Wait for message to be received
        val received = latch.await(5, TimeUnit.SECONDS)
        
        assertTrue(received, "Message should be received within timeout")
        assertNotNull(receivedMessage)
        assertEquals("survival", receivedMessage?.serverName)
        assertEquals("TestPlayer", receivedMessage?.playerName)
        assertEquals("Hello from integration test!", receivedMessage?.rawMessage)
    }

    @Test
    fun `should publish and receive ServerPlayerInfo heartbeat via Redis`() {
        val latch = CountDownLatch(1)
        var receivedInfo: ServerPlayerInfo? = null
        
        messageBus.subscribe("magnus:playerlist") { payload ->
            receivedInfo = json.decodeFromString<ServerPlayerInfo>(payload)
            latch.countDown()
        }
        
        Thread.sleep(500)
        
        val playerInfo = ServerPlayerInfo(
            serverName = "lobby",
            players = listOf(
                PlayerEntry("uuid-1", "Player1"),
                PlayerEntry("uuid-2", "Player2")
            )
        )
        messageBus.publish("magnus:playerlist", json.encodeToString(playerInfo))
        
        val received = latch.await(5, TimeUnit.SECONDS)
        
        assertTrue(received, "Heartbeat should be received within timeout")
        assertNotNull(receivedInfo)
        assertEquals("lobby", receivedInfo?.serverName)
        assertEquals(2, receivedInfo?.players?.size)
    }

    @Test
    fun `should handle multiple subscribers on same channel`() {
        val latch = CountDownLatch(2)
        val receivedMessages = mutableListOf<String>()
        
        // Two buses simulating two servers
        val messageBus2 = RedisMessageBus(jedisPool)
        
        messageBus.subscribe("magnus:chat") { payload ->
            synchronized(receivedMessages) {
                receivedMessages.add("bus1: $payload")
            }
            latch.countDown()
        }
        
        messageBus2.subscribe("magnus:chat") { payload ->
            synchronized(receivedMessages) {
                receivedMessages.add("bus2: $payload")
            }
            latch.countDown()
        }
        
        Thread.sleep(500)
        
        val chatMessage = ChatMessage(
            serverName = "creative",
            playerUuid = "uuid",
            playerName = "Sender",
            rawMessage = "Broadcast test"
        )
        messageBus.publish("magnus:chat", json.encodeToString(chatMessage))
        
        val received = latch.await(5, TimeUnit.SECONDS)
        
        assertTrue(received)
        assertEquals(2, receivedMessages.size)
        
        messageBus2.close()
    }

    @Test
    fun `should handle rapid message publishing`() {
        val messageCount = 10
        val latch = CountDownLatch(messageCount)
        val receivedMessages = mutableListOf<ChatMessage>()
        
        messageBus.subscribe("magnus:chat") { payload ->
            synchronized(receivedMessages) {
                receivedMessages.add(json.decodeFromString<ChatMessage>(payload))
            }
            latch.countDown()
        }
        
        Thread.sleep(500)
        
        // Publish many messages rapidly
        repeat(messageCount) { i ->
            val msg = ChatMessage(
                serverName = "server-$i",
                playerUuid = "uuid-$i",
                playerName = "Player$i",
                rawMessage = "Message $i"
            )
            messageBus.publish("magnus:chat", json.encodeToString(msg))
        }
        
        val received = latch.await(10, TimeUnit.SECONDS)
        
        assertTrue(received, "All $messageCount messages should be received")
        assertEquals(messageCount, receivedMessages.size)
    }
}
