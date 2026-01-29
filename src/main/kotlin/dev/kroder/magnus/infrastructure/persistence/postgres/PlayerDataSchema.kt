package dev.kroder.magnus.infrastructure.persistence.postgres

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

/**
 * SQL Table definition for player data using Exposed.
 * 
 * Functional Limits:
 * - Inventory and Ender Chest NBT are stored as text (could be JSONB if optimized for Postgres specifically).
 * - Primary key is the player's UUID.
 */
object PlayerDataTable : Table("player_data") {
    val uuid = uuid("uuid")
    val username = varchar("username", 32)
    val health = float("health")
    val foodLevel = integer("food_level")
    val saturation = float("saturation")
    val experienceLevel = integer("experience_level")
    val experienceProgress = float("experience_progress")
    val inventoryNbt = text("inventory_nbt")
    val enderChestNbt = text("ender_chest_nbt")
    val lastUpdated = long("last_updated")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(uuid)
}
