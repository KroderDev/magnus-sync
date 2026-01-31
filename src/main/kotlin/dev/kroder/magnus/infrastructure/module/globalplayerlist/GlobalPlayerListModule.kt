package dev.kroder.magnus.infrastructure.module.globalplayerlist

import dev.kroder.magnus.application.GlobalPlayerListService
import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.domain.module.MagnusModule
import dev.kroder.magnus.infrastructure.fabric.GlistCommand
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Module for global player list synchronization.
 * Publishes local player count via heartbeat and maintains a global player map.
 * Provides /glist command for viewing global player list.
 */
class GlobalPlayerListModule(
    private val messageBus: MessageBus,
    private val serverName: String
) : MagnusModule {

    override val id = "global-player-list"
    override val name = "Global Player List"

    private val logger = LoggerFactory.getLogger("magnus-global-playerlist")
    
    private lateinit var playerListService: GlobalPlayerListService
    private var server: MinecraftServer? = null

    override fun onEnable() {
        playerListService = GlobalPlayerListService(messageBus, serverName)

        // Subscribe to Redis channel for incoming heartbeats
        messageBus.subscribe(GlobalPlayerListService.CHANNEL) { payload ->
            playerListService.onHeartbeatReceived(payload)
        }

        // Register /glist command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            GlistCommand(playerListService).register(dispatcher)
        }

        // Start heartbeat when server is ready
        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            playerListService.startHeartbeat(srv)
            logger.info("Global Player List module activated for server: $serverName")
        }

        // Stop heartbeat when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register {
            playerListService.stopHeartbeat()
        }

        logger.info("Global Player List module enabled")
    }

    override fun onDisable() {
        playerListService.shutdown()
        server = null
        logger.info("Global Player List module disabled")
    }
}
