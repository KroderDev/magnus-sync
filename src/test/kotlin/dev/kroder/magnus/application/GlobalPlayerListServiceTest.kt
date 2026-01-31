package dev.kroder.magnus.application

import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.domain.model.PlayerEntry
import dev.kroder.magnus.domain.model.ServerPlayerInfo
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for GlobalPlayerListService.
 * Tests focus on service logic without Minecraft dependencies.
 */
class GlobalPlayerListServiceTest {
    private lateinit var messageBus: MessageBus
    private lateinit var service: GlobalPlayerListService
    private val json = Json { ignoreUnknownKeys = true }
    
    private val serverName = "test-server"

    @BeforeEach
    fun setup() {
        messageBus = mockk(relaxed = true)
        service = GlobalPlayerListService(messageBus, serverName)
    }

    @Test
    fun `should update player map on heartbeat received`() {
        val info = ServerPlayerInfo(
            serverName = "other-server",
            players = listOf(
                PlayerEntry("uuid-1", "Player1"),
                PlayerEntry("uuid-2", "Player2")
            )
        )
        val payload = json.encodeToString(info)

        service.onHeartbeatReceived(payload)

        val playersByServer = service.getPlayersByServer()
        assertTrue(playersByServer.containsKey("other-server"))
        assertEquals(listOf("Player1", "Player2"), playersByServer["other-server"])
    }

    @Test
    fun `should replace existing server entry on new heartbeat`() {
        // First heartbeat
        val info1 = ServerPlayerInfo(
            serverName = "other-server",
            players = listOf(PlayerEntry("uuid-1", "OldPlayer"))
        )
        service.onHeartbeatReceived(json.encodeToString(info1))

        // Second heartbeat with different players
        val info2 = ServerPlayerInfo(
            serverName = "other-server",
            players = listOf(PlayerEntry("uuid-2", "NewPlayer"))
        )
        service.onHeartbeatReceived(json.encodeToString(info2))

        val playersByServer = service.getPlayersByServer()
        assertEquals(listOf("NewPlayer"), playersByServer["other-server"])
    }

    @Test
    fun `should calculate global player count correctly`() {
        // Add players from multiple servers
        val info1 = ServerPlayerInfo(
            serverName = "server-a",
            players = listOf(
                PlayerEntry("uuid-1", "Player1"),
                PlayerEntry("uuid-2", "Player2")
            )
        )
        val info2 = ServerPlayerInfo(
            serverName = "server-b",
            players = listOf(
                PlayerEntry("uuid-3", "Player3")
            )
        )

        service.onHeartbeatReceived(json.encodeToString(info1))
        service.onHeartbeatReceived(json.encodeToString(info2))

        assertEquals(3, service.getGlobalPlayerCount())
    }

    @Test
    fun `should return players grouped by server`() {
        val info1 = ServerPlayerInfo(
            serverName = "survival",
            players = listOf(PlayerEntry("uuid-1", "SurvivalPlayer"))
        )
        val info2 = ServerPlayerInfo(
            serverName = "lobby",
            players = listOf(PlayerEntry("uuid-2", "LobbyPlayer"))
        )

        service.onHeartbeatReceived(json.encodeToString(info1))
        service.onHeartbeatReceived(json.encodeToString(info2))

        val grouped = service.getPlayersByServer()
        assertEquals(2, grouped.size)
        assertEquals(listOf("SurvivalPlayer"), grouped["survival"])
        assertEquals(listOf("LobbyPlayer"), grouped["lobby"])
    }

    @Test
    fun `should handle empty player list`() {
        val info = ServerPlayerInfo(
            serverName = "empty-server",
            players = emptyList()
        )

        service.onHeartbeatReceived(json.encodeToString(info))

        val playersByServer = service.getPlayersByServer()
        assertTrue(playersByServer.containsKey("empty-server"))
        assertTrue(playersByServer["empty-server"]!!.isEmpty())
    }

    @Test
    fun `should handle malformed heartbeat JSON gracefully`() {
        assertDoesNotThrow {
            service.onHeartbeatReceived("invalid json {{{")
        }
        
        // Map should remain functional
        assertEquals(0, service.getGlobalPlayerCount())
    }

    @Test
    fun `should cleanup stale entries after timeout`() {
        // Add an old entry with timestamp in the past (more than 10s ago)
        val staleInfo = ServerPlayerInfo(
            serverName = "stale-server",
            players = listOf(PlayerEntry("uuid", "StalePlayer")),
            timestamp = System.currentTimeMillis() - 15_000 // 15 seconds ago
        )
        
        service.onHeartbeatReceived(json.encodeToString(staleInfo))

        // Now add a fresh entry to trigger cleanup
        val freshInfo = ServerPlayerInfo(
            serverName = "fresh-server",
            players = listOf(PlayerEntry("uuid", "FreshPlayer"))
        )
        service.onHeartbeatReceived(json.encodeToString(freshInfo))

        val playersByServer = service.getPlayersByServer()
        
        // Stale entry should be removed, fresh should remain
        assertFalse(playersByServer.containsKey("stale-server"))
        assertTrue(playersByServer.containsKey("fresh-server"))
    }

    @Test
    fun `should shutdown cleanly`() {
        val info = ServerPlayerInfo(
            serverName = "some-server",
            players = listOf(PlayerEntry("uuid", "Player"))
        )
        service.onHeartbeatReceived(json.encodeToString(info))

        service.shutdown()

        // After shutdown, map should be cleared
        assertEquals(0, service.getGlobalPlayerCount())
    }
}
