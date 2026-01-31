package dev.kroder.magnus.application

import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.domain.model.ChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

/**
 * Application service for global chat synchronization.
 * Publishes local chat messages to Redis and broadcasts incoming messages to local players.
 */
class GlobalChatService(
    private val messageBus: MessageBus,
    private val serverName: String
) {
    private val logger = LoggerFactory.getLogger("magnus-global-chat")
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val CHANNEL = "magnus:chat"
    }

    /**
     * Publishes a chat message to the global channel.
     * @param playerUuid UUID of the sender
     * @param playerName Display name of the sender
     * @param rawMessage Raw text content (no formatting)
     */
    fun publishMessage(playerUuid: String, playerName: String, rawMessage: String) {
        try {
            val message = ChatMessage(
                serverName = serverName,
                playerUuid = playerUuid,
                playerName = playerName,
                rawMessage = rawMessage
            )
            val payload = json.encodeToString(message)
            messageBus.publish(CHANNEL, payload)
            logger.debug("Published chat message from $playerName")
        } catch (e: Exception) {
            logger.error("Failed to publish chat message: ${e.message}", e)
        }
    }

    /**
     * Handles an incoming chat message from Redis.
     * Broadcasts to all local players if the message is from another server.
     */
    fun onMessageReceived(payload: String, server: MinecraftServer?) {
        if (server == null) return
        
        try {
            val message = json.decodeFromString<ChatMessage>(payload)
            
            // Ignore messages from this server to prevent echo
            if (message.serverName == serverName) return
            
            // Broadcast raw message to all local players
            // The message is sent as-is to preserve any formatting from the source server
            val text = Text.literal("<${message.playerName}> ${message.rawMessage}")
            
            server.playerManager.playerList.forEach { player ->
                player.sendMessage(text, false)
            }
            
            logger.debug("Received chat from ${message.serverName}: ${message.playerName}")
        } catch (e: Exception) {
            logger.error("Failed to process incoming chat message: ${e.message}", e)
        }
    }
}
