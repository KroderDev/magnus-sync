package dev.kroder.magnus.mixin

import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerLoginNetworkHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/**
 * Accessor Mixin to expose the private 'profile' field from ServerLoginNetworkHandler.
 * This is used by LoginQueueHandler to get the player's UUID during login.
 */
@Mixin(ServerLoginNetworkHandler::class)
interface ServerLoginNetworkHandlerAccessor {
    
    @Accessor("profile")
    fun getProfile(): GameProfile?
}
