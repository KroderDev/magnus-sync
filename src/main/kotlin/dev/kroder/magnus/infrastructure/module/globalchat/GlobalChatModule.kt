package dev.kroder.magnus.infrastructure.module.globalchat

import dev.kroder.magnus.application.GlobalChatService
import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.domain.module.MagnusModule
import dev.kroder.magnus.infrastructure.fabric.ChatEventListener
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Module for global chat synchronization across servers.
 * Listens to local chat events and broadcasts them via Redis.
 * Receives chat from other servers and displays to local players.
 */
class GlobalChatModule(
    private val messageBus: MessageBus,
    private val serverName: String
) : MagnusModule {

    override val id = "global-chat"
    override val name = "Global Chat"

    private val logger = LoggerFactory.getLogger("magnus-global-chat")
    
    private lateinit var chatService: GlobalChatService
    private lateinit var chatListener: ChatEventListener
    private var server: MinecraftServer? = null

    override fun onEnable() {
        chatService = GlobalChatService(messageBus, serverName)
        chatListener = ChatEventListener(chatService)

        // Subscribe to Redis channel for incoming messages
        messageBus.subscribe(GlobalChatService.CHANNEL) { payload ->
            chatService.onMessageReceived(payload, server)
        }

        // Register for server started event to get server reference
        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            chatListener.register()
            logger.info("Global Chat module activated for server: $serverName")
        }

        logger.info("Global Chat module enabled")
    }

    override fun onDisable() {
        server = null
        logger.info("Global Chat module disabled")
    }
}
