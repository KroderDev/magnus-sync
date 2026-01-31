package dev.kroder.magnus.infrastructure.fabric

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import dev.kroder.magnus.application.GlobalPlayerListService
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * /glist command implementation.
 * Displays global player count and list by server.
 */
class GlistCommand(
    private val playerListService: GlobalPlayerListService
) {

    /**
     * Registers the /glist command with the dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("glist")
                .executes { context -> execute(context) }
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        
        try {
            val totalPlayers = playerListService.getGlobalPlayerCount()
            val playersByServer = playerListService.getPlayersByServer()
            
            // Header
            source.sendFeedback(
                { Text.literal("§6§l=== Global Player List ===") },
                false
            )
            
            source.sendFeedback(
                { Text.literal("§7Total players online: §f$totalPlayers") },
                false
            )
            
            // Empty line
            source.sendFeedback({ Text.literal("") }, false)
            
            if (playersByServer.isEmpty()) {
                source.sendFeedback(
                    { Text.literal("§8No servers connected.") },
                    false
                )
            } else {
                // List each server and its players
                playersByServer.forEach { (serverName, players) ->
                    val playerCount = players.size
                    val playerNames = if (players.isEmpty()) {
                        "§8(empty)"
                    } else {
                        players.joinToString(", ") { "§f$it" }
                    }
                    
                    source.sendFeedback(
                        { Text.literal("§a$serverName §7[$playerCount]: $playerNames") },
                        false
                    )
                }
            }
            
            return 1
        } catch (e: Exception) {
            source.sendError(Text.literal("§cError fetching global player list."))
            return 0
        }
    }
}
