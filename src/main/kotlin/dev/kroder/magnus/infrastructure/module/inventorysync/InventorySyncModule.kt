package dev.kroder.magnus.infrastructure.module.inventorysync

import dev.kroder.magnus.application.SyncService
import dev.kroder.magnus.domain.module.MagnusModule
import dev.kroder.magnus.infrastructure.fabric.PlayerEventListener
import org.slf4j.LoggerFactory

/**
 * Module for player inventory synchronization across servers.
 * 
 * This module handles loading player data (inventory, health, XP, etc.) 
 * when they join and saving it when they disconnect.
 * 
 * Enabled by default, but can be disabled for lobby/hub servers
 * where only global chat or tablist is needed.
 */
class InventorySyncModule(
    private val syncService: SyncService
) : MagnusModule {

    override val id = "inventory-sync"
    override val name = "Inventory Sync"

    private val logger = LoggerFactory.getLogger("magnus-inventory-sync")
    
    private lateinit var playerEventListener: PlayerEventListener

    override fun onEnable() {
        playerEventListener = PlayerEventListener(syncService)
        playerEventListener.register()
        
        logger.info("Inventory Sync module enabled - Player data will be synchronized")
    }

    override fun onDisable() {
        // Note: Fabric API does not provide a native way to unregister event callbacks.
        // The listener will remain registered but the module is logically disabled.
        logger.info("Inventory Sync module disabled (event listeners cannot be unregistered in Fabric)")
    }
}
