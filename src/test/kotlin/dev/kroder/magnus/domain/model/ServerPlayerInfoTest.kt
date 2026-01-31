package dev.kroder.magnus.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerPlayerInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should serialize with player list correctly`() {
        val original = ServerPlayerInfo(
            serverName = "survival",
            players = listOf(
                PlayerEntry("uuid-1", "Player1"),
                PlayerEntry("uuid-2", "Player2"),
                PlayerEntry("uuid-3", "Player3")
            ),
            timestamp = 1706654400000
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ServerPlayerInfo>(serialized)

        assertEquals(original, deserialized)
        assertEquals(3, deserialized.players.size)
    }

    @Test
    fun `should handle empty player list`() {
        val original = ServerPlayerInfo(
            serverName = "empty-server",
            players = emptyList(),
            timestamp = 1706654400000
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ServerPlayerInfo>(serialized)

        assertEquals(original, deserialized)
        assertTrue(deserialized.players.isEmpty())
    }

    @Test
    fun `should set default timestamp when not provided`() {
        val before = System.currentTimeMillis()
        
        val info = ServerPlayerInfo(
            serverName = "test",
            players = listOf(PlayerEntry("uuid", "Player"))
        )
        
        val after = System.currentTimeMillis()

        assertTrue(info.timestamp >= before)
        assertTrue(info.timestamp <= after)
    }

    @Test
    fun `PlayerEntry should serialize correctly`() {
        val entry = PlayerEntry(
            uuid = "550e8400-e29b-41d4-a716-446655440000",
            name = "TestPlayer"
        )

        val serialized = json.encodeToString(entry)
        val deserialized = json.decodeFromString<PlayerEntry>(serialized)

        assertEquals(entry, deserialized)
    }
}
