package dev.kroder.magnus.infrastructure.persistence.postgres

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Implementation of [PlayerRepository] using PostgreSQL and Exposed.
 * This adapter handles the persistence of player data to a relational database.
 * 
 * Functional Limits:
 * - Block transactions are used, which may impact performance if not managed.
 * - This implementation stores data permanently.
 */
class PostgresPlayerRepository(private val database: Database) : PlayerRepository {

    override fun save(data: PlayerData) {
        transaction(database) {
            PlayerDataTable.upsert {
                it[uuid] = data.uuid
                it[username] = data.username
                it[health] = data.health
                it[foodLevel] = data.foodLevel
                it[saturation] = data.saturation
                it[experienceLevel] = data.experienceLevel
                it[experienceProgress] = data.experienceProgress
                it[inventoryNbt] = data.inventoryNbt
                it[enderChestNbt] = data.enderChestNbt
                it[lastUpdated] = data.lastUpdated
            }
        }
    }

    override fun findByUuid(uuid: UUID): PlayerData? {
        return transaction(database) {
            PlayerDataTable.select(PlayerDataTable.uuid eq uuid)
                .map {
                    PlayerData(
                        uuid = it[PlayerDataTable.uuid],
                        username = it[PlayerDataTable.username],
                        health = it[PlayerDataTable.health],
                        foodLevel = it[PlayerDataTable.foodLevel],
                        saturation = it[PlayerDataTable.saturation],
                        experienceLevel = it[PlayerDataTable.experienceLevel],
                        experienceProgress = it[PlayerDataTable.experienceProgress],
                        inventoryNbt = it[PlayerDataTable.inventoryNbt],
                        enderChestNbt = it[PlayerDataTable.enderChestNbt],
                        lastUpdated = it[PlayerDataTable.lastUpdated]
                    )
                }
                .singleOrNull()
        }
    }

    override fun deleteCache(uuid: UUID) {
        // Postgres is persistent, so deleteCache does nothing here.
        // Cache eviction is handled by the Redis implementation.
    }
}
