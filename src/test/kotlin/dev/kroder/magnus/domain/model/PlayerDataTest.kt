package dev.kroder.magnus.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerDataTest {

    private val json = Json { 
        prettyPrint = true 
        allowSpecialFloatingPointValues = true
    }

    @Test
    fun `should serialize and deserialize PlayerData correctly`() {
        val fixedTimestamp = 1234567890L
        val original = PlayerData(
            uuid = UUID.randomUUID(),
            username = "TestUser",
            health = 20.0f,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            air = 300,
            score = 100,
            selectedSlot = 0,
            experienceLevel = 10,
            experienceProgress = 0.5f,
            inventoryNbt = "{}",
            enderChestNbt = "{}",
            activeEffectsNbt = "[]",
            lastUpdated = fixedTimestamp
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<PlayerData>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `should serialize UUID as string`() {
        val uuid = UUID.randomUUID()
        val original = PlayerData(
            uuid = uuid,
            username = "TestUser",
            health = 20.0f,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            air = 300,
            score = 100,
            selectedSlot = 0,
            experienceLevel = 10,
            experienceProgress = 0.5f,
            inventoryNbt = "{}",
            enderChestNbt = "{}",
            activeEffectsNbt = "[]"
        )

        val serialized = json.encodeToString(original)
        assertTrue(serialized.contains("\"uuid\": \"$uuid\""))
    }

    @Test
    fun `should handle edge case values`() {
        val original = PlayerData(
            uuid = UUID.randomUUID(),
            username = "TestUser",
            health = Float.MAX_VALUE,
            foodLevel = Int.MAX_VALUE,
            saturation = Float.MIN_VALUE,
            exhaustion = Float.POSITIVE_INFINITY, // Explicitly testing infinity
            air = Int.MIN_VALUE,
            score = Int.MAX_VALUE,
            selectedSlot = 0,
            experienceLevel = 0,
            experienceProgress = 0.0f,
            inventoryNbt = "", // Empty NBT string
            enderChestNbt = "{}",
            activeEffectsNbt = "[]"
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<PlayerData>(serialized)

        assertEquals(original.health, deserialized.health)
        assertEquals(original.foodLevel, deserialized.foodLevel)
        assertEquals(original.air, deserialized.air)
        assertEquals(original.inventoryNbt, deserialized.inventoryNbt)
    }

    @Test
    fun `should handle special characters in username`() {
        val specialName = "User_Name!@#$%^&*()"
        val original = PlayerData(
            uuid = UUID.randomUUID(),
            username = specialName,
            health = 20.0f,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            air = 300,
            score = 100,
            selectedSlot = 0,
            experienceLevel = 10,
            experienceProgress = 0.5f,
            inventoryNbt = "{}",
            enderChestNbt = "{}",
            activeEffectsNbt = "[]"
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<PlayerData>(serialized)

        assertEquals(specialName, deserialized.username)
    }
}
