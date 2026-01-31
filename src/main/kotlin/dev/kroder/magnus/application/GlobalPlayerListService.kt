package dev.kroder.magnus.application

import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.domain.model.PlayerEntry
import dev.kroder.magnus.domain.model.ServerPlayerInfo
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Application service for global player list synchronization.
 * Maintains an in-memory map of all players across all servers via heartbeat.
 */
class GlobalPlayerListService(
    private val messageBus: MessageBus,
    private val serverName: String
) {
    private val logger = LoggerFactory.getLogger("magnus-global-playerlist")
    private val json = Json { ignoreUnknownKeys = true }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    
    // In-memory map: serverName -> ServerPlayerInfo
    private val serverPlayers = ConcurrentHashMap<String, ServerPlayerInfo>()
    
    // Timeout for stale entries (10 seconds - if no heartbeat in 10s, consider offline)
    private val staleTimeoutMs = 10_000L

    companion object {
        const val CHANNEL = "magnus:playerlist"
        const val HEARTBEAT_INTERVAL_MS = 2500L // 2.5 seconds
    }

    /**
     * Starts the heartbeat loop that publishes local player list to Redis.
     */
    fun startHeartbeat(server: MinecraftServer) {
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    publishHeartbeat(server)
                } catch (e: Exception) {
                    logger.error("Heartbeat failed: ${e.message}", e)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        logger.info("Global player list heartbeat started (interval: ${HEARTBEAT_INTERVAL_MS}ms)")
    }

    /**
     * Stops the heartbeat loop.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        logger.info("Global player list heartbeat stopped")
    }

    /**
     * Publishes the current local player list to Redis.
     */
    private fun publishHeartbeat(server: MinecraftServer) {
        val players = server.playerManager.playerList.map { player ->
            PlayerEntry(
                uuid = player.uuid.toString(),
                name = player.gameProfile.name
            )
        }
        
        val info = ServerPlayerInfo(
            serverName = serverName,
            players = players
        )
        
        // Also update our own entry locally
        serverPlayers[serverName] = info
        
        val payload = json.encodeToString(info)
        messageBus.publish(CHANNEL, payload)
    }

    /**
     * Handles an incoming heartbeat from Redis.
     */
    fun onHeartbeatReceived(payload: String) {
        try {
            val info = json.decodeFromString<ServerPlayerInfo>(payload)
            serverPlayers[info.serverName] = info
            cleanupStaleEntries()
        } catch (e: Exception) {
            logger.error("Failed to process heartbeat: ${e.message}", e)
        }
    }

    /**
     * Removes server entries that haven't sent a heartbeat recently.
     */
    private fun cleanupStaleEntries() {
        val now = System.currentTimeMillis()
        serverPlayers.entries.removeIf { (name, info) ->
            name != serverName && (now - info.timestamp) > staleTimeoutMs
        }
    }

    /**
     * Returns the total player count across all servers.
     */
    fun getGlobalPlayerCount(): Int {
        cleanupStaleEntries()
        return serverPlayers.values.sumOf { it.players.size }
    }

    /**
     * Returns a map of server name -> list of player names.
     */
    fun getPlayersByServer(): Map<String, List<String>> {
        cleanupStaleEntries()
        return serverPlayers.mapValues { (_, info) ->
            info.players.map { it.name }
        }
    }

    /**
     * Shuts down the service and cancels all coroutines.
     */
    fun shutdown() {
        stopHeartbeat()
        scope.cancel()
        serverPlayers.clear()
    }
}
