package dev.kroder.magnus.domain.module

/**
 * Interface that all Magnus modules must implement.
 * This ensures a consistent lifecycle and management.
 */
interface MagnusModule {
    val name: String
    val id: String

    /**
     * Called when the module is being enabled.
     * All initializations, event registrations, and resource acquisitions
     * should happen here.
     */
    fun onEnable()

    /**
     * Called when the module is being disabled.
     * Clean up all resources, unregister listeners, and stop background tasks here.
     */
    fun onDisable()
}
