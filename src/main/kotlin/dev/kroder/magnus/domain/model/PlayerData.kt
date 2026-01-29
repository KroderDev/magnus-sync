package dev.kroder.magnus.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * Represents the core data of a player that needs to be synchronized between servers.
 * This is a pure Kotlin data class, independent of the Minecraft API.
 * 
 * Functional Limits:
 * - This model only stores data that can be serialized and moved across networks.
 * - It does not handle Minecraft-specific objects like [ServerPlayerEntity].
 */
@Serializable
data class PlayerData(
    @Serializable(with = UuidSerializer::class)
    val uuid: UUID,
    val username: String,
    val health: Float,
    val foodLevel: Int,
    val saturation: Float,
    val experienceLevel: Int,
    val experienceProgress: Float,
    val inventoryNbt: String, // Base64 encoded NBT or JSON representation
    val enderChestNbt: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}
