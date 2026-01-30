package dev.kroder.magnus.login

import dev.kroder.magnus.Magnus
import dev.kroder.magnus.mixin.ServerLoginNetworkHandlerAccessor
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture

/**
 * Handles login synchronization using Fabric API events.
 * 
 * This replaces the previous Mixin approach and uses the official Fabric API
 * `ServerLoginConnectionEvents.QUERY_START` with `LoginSynchronizer` to delay
 * login until the player's session lock is available.
 */
object LoginQueueHandler {

    private const val MAX_WAIT_MS = 20_000L // 20 seconds timeout
    private const val POLL_INTERVAL_MS = 100L // Poll every 100ms

    /**
     * Registers the login synchronization handler.
     * Should be called during mod initialization.
     */
    fun register() {
        ServerLoginConnectionEvents.QUERY_START.register { handler, server, sender, synchronizer ->
            // Use the accessor mixin to get the private profile field
            val accessor = handler as ServerLoginNetworkHandlerAccessor
            val profile = accessor.getProfile()
            
            if (profile == null) {
                Magnus.logger.warn("LoginQueueHandler: No profile available during QUERY_START")
                return@register
            }

            val playerId = profile.id
            if (playerId == null) {
                Magnus.logger.warn("LoginQueueHandler: No player ID available for profile ${profile.name}")
                return@register
            }

            Magnus.logger.info("LoginQueueHandler: Checking session lock for player ${profile.name} (${playerId})")

            // Register a synchronization task that waits for the session lock to be released
            synchronizer.waitFor(CompletableFuture.supplyAsync {
                waitForSessionUnlock(playerId, profile.name ?: "Unknown")
            })
        }

        Magnus.logger.info("LoginQueueHandler: Registered login synchronization handler")
    }

    /**
     * Waits for the session lock to be released, with timeout.
     * Returns null on success, or throws an exception on timeout.
     */
    private fun waitForSessionUnlock(playerId: java.util.UUID, playerName: String): Void? {
        val startTime = System.currentTimeMillis()
        
        try {
            val service = Magnus.syncService
            
            while (service.isSessionLocked(playerId)) {
                val elapsed = System.currentTimeMillis() - startTime
                
                if (elapsed > MAX_WAIT_MS) {
                    Magnus.logger.warn("LoginQueueHandler: Timeout waiting for session unlock for $playerName ($playerId)")
                    throw RuntimeException("Session Sync Timeout: Could not acquire lock after ${MAX_WAIT_MS / 1000}s. Please try again.")
                }
                
                // Log periodically (every 5 seconds)
                if ((elapsed / 1000) % 5 == 0L && elapsed > 0) {
                    Magnus.logger.debug("LoginQueueHandler: Waiting for session unlock for $playerName... (${elapsed / 1000}s)")
                }
                
                Thread.sleep(POLL_INTERVAL_MS)
            }
            
            Magnus.logger.info("LoginQueueHandler: Session lock cleared for $playerName, proceeding with login")
            return null
            
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Login synchronization interrupted", e)
        } catch (e: Exception) {
            if (e is RuntimeException) throw e
            Magnus.logger.error("LoginQueueHandler: Error checking session lock for $playerName", e)
            // On error, allow login to proceed (fail-open behavior)
            return null
        }
    }
}
