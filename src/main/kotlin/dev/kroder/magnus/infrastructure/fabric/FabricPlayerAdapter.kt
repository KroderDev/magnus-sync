package dev.kroder.magnus.infrastructure.fabric

import dev.kroder.magnus.domain.model.PlayerData
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.server.network.ServerPlayerEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Adapter that bridges the Minecraft API with our Domain models.
 * It handles the extraction of data from [ServerPlayerEntity] and the injection of data back into it.
 * 
 * Functional Limits:
 * - NBT data is serialized to Base64 for database storage.
 */
object FabricPlayerAdapter {

    /**
     * Creates a [PlayerData] snapshot from a live Minecraft player.
     */
    fun toDomain(player: ServerPlayerEntity): PlayerData {
        // PlayerInventory has writeNbt
        val invCompound = NbtCompound()
        player.inventory.writeNbt(invCompound.getList("Inventory", 10))

        // EnderChestInventory does NOT have writeNbt, need to use toNbtList
        val enderCompound = NbtCompound()
        val enderList = player.enderChestInventory.toNbtList(player.registryManager)
        enderCompound.put("EnderItems", enderList)

        return PlayerData(
            uuid = player.uuid,
            username = player.gameProfile.name,
            health = player.health,
            foodLevel = player.hungerManager.foodLevel,
            saturation = player.hungerManager.saturationLevel,
            experienceLevel = player.experienceLevel,
            experienceProgress = player.experienceProgress,
            inventoryNbt = serializeNbt(invCompound),
            enderChestNbt = serializeNbt(enderCompound)
        )
    }

    /**
     * Applies [PlayerData] back into a live Minecraft player.
     */
    fun applyToPlayer(player: ServerPlayerEntity, data: PlayerData) {
        player.health = data.health
        player.hungerManager.foodLevel = data.foodLevel
        player.hungerManager.saturationLevel = data.saturation
        player.setExperienceLevel(data.experienceLevel)
        player.experienceProgress = data.experienceProgress

        val invCompound = deserializeNbt(data.inventoryNbt)
        player.inventory.readNbt(invCompound.getList("Inventory", 10))

        val enderCompound = deserializeNbt(data.enderChestNbt)
        // EnderChestInventory does NOT have readNbt, need to use readNbtList
        player.enderChestInventory.readNbtList(enderCompound.getList("EnderItems", 10), player.registryManager)
        
        // Mark as dirty to ensure sync with client
        player.inventory.markDirty()
        player.enderChestInventory.markDirty()
    }

    private fun serializeNbt(nbt: NbtCompound): String {
        val baos = ByteArrayOutputStream()
        NbtIo.writeCompressed(nbt, baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun deserializeNbt(base64: String): NbtCompound {
        if (base64.isEmpty()) return NbtCompound()
        val bytes = Base64.getDecoder().decode(base64)
        // NbtIo.readCompressed requires NbtSizeTracker in newer versions
        return NbtIo.readCompressed(ByteArrayInputStream(bytes), net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes())
    }
}
