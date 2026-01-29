package dev.kroder.magnus.infrastructure.module

import dev.kroder.magnus.domain.module.MagnusModule
import org.slf4j.LoggerFactory

/**
 * Manages the lifecycle of Magnus modules.
 * Responsible for enabling, disabling, and tracking the state of all modules.
 */
class ModuleManager {
    private val logger = LoggerFactory.getLogger("magnus-modules")
    private val modules = mutableMapOf<String, MagnusModule>()
    private val activeModules = mutableSetOf<String>()

    /**
     * Registers a module to the manager.
     */
    fun registerModule(module: MagnusModule) {
        if (modules.containsKey(module.id)) {
            logger.warn("Module with ID '${module.id}' is already registered!")
            return
        }
        modules[module.id] = module
        logger.info("Module registered: ${module.name} (${module.id})")
    }

    /**
     * Enables a module by its ID if it hasn't been enabled yet.
     */
    fun enableModule(id: String) {
        val module = modules[id]
        if (module == null) {
            logger.error("Failed to enable module '$id': Module not found.")
            return
        }
        if (activeModules.contains(id)) return

        try {
            logger.info("Enabling module: ${module.name}...")
            module.onEnable()
            activeModules.add(id)
            logger.info("Module '${module.name}' enabled successfully.")
        } catch (e: Exception) {
            logger.error("CRITICAL: Failed to enable module '${module.name}': ${e.message}", e)
        }
    }

    /**
     * Disables an active module by its ID.
     */
    fun disableModule(id: String) {
        if (!activeModules.contains(id)) {
            logger.debug("Cannot disable module '$id': Not active.")
            return
        }
        
        val module = modules[id] ?: return

        try {
            logger.info("Disabling module: ${module.name}...")
            module.onDisable()
            activeModules.remove(id)
            logger.info("Module '${module.name}' disabled successfully.")
        } catch (e: Exception) {
            logger.error("Error during disable of module '${module.name}': ${e.message}", e)
        }
    }

    /**
     * Disables all active modules in reverse order of activation or as they come.
     */
    fun shutdown() {
        logger.info("Shutting down ModuleManager and disabling all modules...")
        val idsToDisable = activeModules.toList()
        idsToDisable.forEach { disableModule(it) }
        logger.info("All modules have been processed for shutdown.")
    }

    /**
     * Returns true if a module is currently active.
     */
    fun isModuleActive(id: String): Boolean = activeModules.contains(id)
}
