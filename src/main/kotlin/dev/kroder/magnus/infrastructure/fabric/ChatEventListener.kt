package dev.kroder.magnus.infrastructure.fabric

import dev.kroder.magnus.application.GlobalChatService
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import org.slf4j.LoggerFactory

/**
 * Fabric event listener for chat messages.
 * Intercepts chat messages and forwards them to the GlobalChatService for Redis publishing.
 */
class ChatEventListener(
    private val chatService: GlobalChatService
) {
    private val logger = LoggerFactory.getLogger("magnus-chat-listener")

    /**
     * Registers the chat event handler.
     */
    fun register() {
        // Listen to chat messages (player-sent messages only)
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            try {
                // Extract raw message content (no formatting to preserve compatibility)
                val rawText = message.content.string
                
                chatService.publishMessage(
                    playerUuid = sender.uuid.toString(),
                    playerName = sender.gameProfile.name,
                    rawMessage = rawText
                )
            } catch (e: Exception) {
                logger.error("Failed to process chat message: ${e.message}", e)
            }
        }
        
        logger.info("Chat event listener registered")
    }
}
