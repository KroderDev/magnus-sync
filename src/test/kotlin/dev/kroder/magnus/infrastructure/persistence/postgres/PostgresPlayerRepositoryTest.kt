package dev.kroder.magnus.infrastructure.persistence.postgres

import dev.kroder.magnus.domain.model.PlayerData
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
class PostgresPlayerRepositoryTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Postgres container is started automatically by @Container
            // Connect Exposed to the container
            Database.connect(
                url = postgres.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = postgres.username,
                password = postgres.password
            )
        }
    }

    private lateinit var repository: PostgresPlayerRepository

    @BeforeEach
    fun prepare() {
        transaction {
            SchemaUtils.create(PlayerDataTable)
            // Clean up table before each test
            PlayerDataTable.deleteAll()
        }
        repository = PostgresPlayerRepository(Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        ))
    }

    private val testUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val testData = PlayerData(
        uuid = testUuid,
        username = "IntegrationUser",
        health = 20f,
        foodLevel = 20,
        saturation = 5f,
        exhaustion = 0f,
        air = 300,
        score = 0,
        selectedSlot = 0,
        experienceLevel = 5,
        experienceProgress = 0.5f,
        inventoryNbt = "{\"Items\":[]}",
        enderChestNbt = "{}",
        activeEffectsNbt = "[]",
        lastUpdated = System.currentTimeMillis()
    )

    @Test
    fun `should save and retrieve player data`() {
        // Save
        repository.save(testData)

        // Retrieve
        val loaded = repository.findByUuid(testUuid)

        // Assert
        assertNotNull(loaded)
        assertEquals(testData.username, loaded?.username)
        assertEquals(testData.health, loaded?.health)
        assertEquals(testData.inventoryNbt, loaded?.inventoryNbt)
    }

    @Test
    fun `should upsert (update) existing player data`() {
        // Initial Save
        repository.save(testData)

        // Update
        val updatedData = testData.copy(health = 10f, experienceLevel = 6)
        repository.save(updatedData)

        // Retrieve and Verify
        val loaded = repository.findByUuid(testUuid)
        assertEquals(10f, loaded?.health)
        assertEquals(6, loaded?.experienceLevel)
    }

    @Test
    fun `should return null for non-existent player`() {
        val loaded = repository.findByUuid(UUID.randomUUID())
        assertEquals(null, loaded)
    }
}
