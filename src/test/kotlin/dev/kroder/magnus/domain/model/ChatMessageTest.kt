package dev.kroder.magnus.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChatMessageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should serialize and deserialize correctly`() {
        val original = ChatMessage(
            serverName = "survival",
            playerUuid = "550e8400-e29b-41d4-a716-446655440000",
            playerName = "TestPlayer",
            rawMessage = "Hello, world!",
            timestamp = 1706654400000
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ChatMessage>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `should set default timestamp when not provided`() {
        val before = System.currentTimeMillis()
        
        val message = ChatMessage(
            serverName = "test",
            playerUuid = "uuid",
            playerName = "Player",
            rawMessage = "Test"
        )
        
        val after = System.currentTimeMillis()

        assertTrue(message.timestamp >= before)
        assertTrue(message.timestamp <= after)
    }

    @Test
    fun `should handle special characters in message`() {
        val message = ChatMessage(
            serverName = "test",
            playerUuid = "uuid",
            playerName = "Player",
            rawMessage = "Hello! @#$%^&*() áéíóú 你好 🎮"
        )

        val serialized = json.encodeToString(message)
        val deserialized = json.decodeFromString<ChatMessage>(serialized)

        assertEquals(message.rawMessage, deserialized.rawMessage)
    }
}
