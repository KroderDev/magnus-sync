package dev.kroder.magnus.gametest

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.infrastructure.fabric.FabricPlayerAdapter
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.network.packet.c2s.common.SyncedClientOptions
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import java.util.UUID
import com.mojang.authlib.GameProfile
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

class MagnusGameTests : FabricGameTest {

    @GameTest
    fun adapterRoundtrip(context: TestContext) {
        // 1. Create a dummy ServerPlayerEntity
        val server = context.world.server
        val world = context.world
        val profile = GameProfile(UUID.randomUUID(), "TestPlayer")
        
        // In 1.21.1, constructor might require SyncedClientOptions or similar
        val clientOptions = SyncedClientOptions.createDefault()
        val player = ServerPlayerEntity(server, world, profile, clientOptions)

        // 2. Setup initial state on the player
        player.health = 10f // Half health
        player.hungerManager.foodLevel = 15
        player.hungerManager.saturationLevel = 2f
        player.inventory.setStack(0, ItemStack(Items.DIAMOND, 64))
        player.setExperienceLevel(5)
        player.experienceProgress = 0.5f

        // 3. Convert to Domain (Snapshot)
        val snapshot: PlayerData = FabricPlayerAdapter.toDomain(player)

        // 4. Verification Check 1: Snapshot matches Player
        if (snapshot.health != 10f) throw RuntimeException("Snapshot Health mismatch: expected 10.0, got ${snapshot.health}")
        if (snapshot.foodLevel != 15) throw RuntimeException("Snapshot Food mismatch: expected 15, got ${snapshot.foodLevel}")
        if (snapshot.experienceLevel != 5) throw RuntimeException("Snapshot XP Level mismatch: expected 5, got ${snapshot.experienceLevel}")
        if (!snapshot.inventoryNbt.contains("id:\"minecraft:diamond\",count:64")) { 
             // Basic NBT check (might need more robust check if format differs, but NbtIo writes descriptive JSON-like internal string sometimes or we rely on it containing the ID)
             // Actually, serializeNbt returns Base64. We can't check string content directly easily.
             // We'll skip string check and rely on Roundtrip.
        }

        // 5. Modify Player (Simulate state change/reset)
        player.health = 20f
        player.inventory.clear()
        player.setExperienceLevel(0)

        // 6. Apply Domain back to Player
        FabricPlayerAdapter.applyToPlayer(player, snapshot)

        // 7. Verification Check 2: Player restored correctly
        if (player.health != 10f) throw RuntimeException("Restored Health mismatch: expected 10.0, got ${player.health}")
        if (player.hungerManager.foodLevel != 15) throw RuntimeException("Restored Food mismatch: expected 15, got ${player.hungerManager.foodLevel}")
        
        val stack = player.inventory.getStack(0)
        if (!stack.isOf(Items.DIAMOND) || stack.count != 64) {
             throw RuntimeException("Restored Inventory mismatch: expected 64 Diamond, got ${stack.count} ${stack.item}")
        }
        
        context.complete()
    }
}
