package dev.kroder.magnus.infrastructure.fabric

import dev.kroder.magnus.application.SyncService
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler

/**
 * Fabric event listeners that drive the synchronization process.
 * 
 * Functional Limits:
 * - Join: Data is loaded from storage and applied to the player.
 * - Disconnect: Data is captured from the player and saved to storage.
 * - This implementation assumes that the "other" server has already saved the data to Redis.
 */
class PlayerEventListener(
    private val syncService: SyncService
) {

    fun register() {
        // Triggered when a player successfully connects to the server
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            onPlayerJoin(handler)
        }

        // Triggered when a player disconnects (lost connection, left, or changing server)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            onPlayerLeave(handler)
        }
    }

    private fun onPlayerJoin(handler: ServerPlayNetworkHandler) {
        val player = handler.player
        try {
            val data = syncService.loadPlayerData(player.uuid)
            
            if (data != null) {
                // Apply the loaded data to the live player instance
                FabricPlayerAdapter.applyToPlayer(player, data)
            }
        } catch (e: dev.kroder.magnus.domain.exception.DataUnavailableException) {
            // CRITICAL: DB is down and no backup. Kick to prevent data loss.
            handler.disconnect(net.minecraft.text.Text.literal("§cSync Error: Database Unavailable.\n§7Please try again later. Your data is safe."))
        } catch (e: Exception) {
            e.printStackTrace()
            // Optional: Kick on generic error too?
        }
    }

    private fun onPlayerLeave(handler: ServerPlayNetworkHandler) {
        val player = handler.player
        val snapshot = FabricPlayerAdapter.toDomain(player)
        
        // Save the snapshot to persistent and cache storage
        syncService.savePlayerData(snapshot)
        
        // Optional: In a multi-proxy setup, we might NOT want to release cache immediately
        // if we know they are going to another server in the same cluster.
        // For now, we release it to ensure freshness.
        syncService.releaseCache(player.uuid)
    }
}
