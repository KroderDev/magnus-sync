package dev.kroder.magnus.mixin

import dev.kroder.magnus.Magnus
import net.minecraft.server.network.ServerLoginNetworkHandler
import net.minecraft.text.Text
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.mojang.authlib.GameProfile
import java.util.concurrent.atomic.AtomicInteger

@Mixin(ServerLoginNetworkHandler::class)
abstract class MixinServerLoginNetworkHandler {

    @Shadow abstract fun acceptPlayer()
    @Shadow private lateinit var profile: GameProfile
    @Shadow private lateinit var state: Any // Shadow as Any to avoid visibility issues with private Enum

    // Atomic counter for the delay/timeout logic
    private val magnus_waitTicks = AtomicInteger(0)
    private val MAGNUS_MAX_WAIT_TICKS = 400 // 20 seconds timeout

    @Inject(method = ["tick"], at = [At("HEAD")], cancellable = true)
    private fun magnus_onTick(ci: CallbackInfo) {
        // We only intervene if the state is "READY_TO_ACCEPT"
        // Use toString() to check enum name without accessing private class
        if (this.state.toString() == "READY_TO_ACCEPT") {
            
            // Access properties safely
            val p = this.profile
            val pId = p.id
            
            if (pId != null) {
                // Check if session is locked
                try {
                    val service = Magnus.syncService
                    if (service.isSessionLocked(pId)) {
                        // It is locked!
                        // 1. Cancel the current tick processing so we don't proceed to ACCEPTED
                        ci.cancel()

                        // 2. Manage timeout/wait
                        val ticks = magnus_waitTicks.incrementAndGet()
                        

                        if (ticks > MAGNUS_MAX_WAIT_TICKS) {
                            // Timeout: Disconnect
                            // Use cast to access disconnect if needed, or calling expected method
                            (this as ServerLoginNetworkHandler).disconnect(Text.literal("§cSession Sync Timeout: Could not acquire lock after 20s."))
                            return
                        }
  
                        // We return early, effectively "pausing" the login in this state.
                        return
                    }
                } catch (e: Exception) {
                    // Fallback in case Magnus isn't initialized or other error
                    e.printStackTrace()
                }
            }
        }
    }
}
