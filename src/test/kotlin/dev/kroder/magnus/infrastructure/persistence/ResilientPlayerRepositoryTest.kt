package dev.kroder.magnus.infrastructure.persistence

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ResilientPlayerRepositoryTest {

    private val primary = mockk<PlayerRepository>(relaxed = true)
    private val backup = mockk<LocalBackupRepository>(relaxed = true)
    private val repository = ResilientPlayerRepository(primary, backup)

    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testData = PlayerData(
        uuid = testUuid,
        username = "TestPlayer",
        health = 20f,
        foodLevel = 20,
        saturation = 5f,
        exhaustion = 0f,
        air = 300,
        score = 0,
        selectedSlot = 0,
        experienceLevel = 0,
        experienceProgress = 0f,
        inventoryNbt = "{}",
        enderChestNbt = "{}",
        activeEffectsNbt = "[]",
        lastUpdated = 1000L
    )

    @Test
    fun `should save to backup when primary fails`() {
        // Given
        every { primary.save(any()) } throws RuntimeException("Connection Failed")

        // When
        repository.save(testData)

        // Then
        verify(exactly = 1) { backup.save(testData) }
    }

    @Test
    fun `should return backup data when primary fails to load`() {
        // Given
        val backupData = testData.copy(lastUpdated = 2000L)
        every { backup.hasBackup(testUuid) } returns true
        every { backup.findByUuid(testUuid) } returns backupData
        every { primary.findByUuid(testUuid) } throws RuntimeException("DB Down")

        // When
        val result = repository.findByUuid(testUuid)

        // Then
        assertNotNull(result)
        assertEquals(backupData, result)
        verify(exactly = 1) { primary.findByUuid(testUuid) }
    }

    @Test
    fun `should prefer backup if fresher than primary`() {
        // Given
        val oldRemoteData = testData.copy(lastUpdated = 1000L)
        val freshLocalData = testData.copy(lastUpdated = 2000L) // Newer!

        every { backup.hasBackup(testUuid) } returns true
        every { backup.findByUuid(testUuid) } returns freshLocalData
        every { primary.findByUuid(testUuid) } returns oldRemoteData

        // When
        val result = repository.findByUuid(testUuid)

        // Then
        assertEquals(freshLocalData, result)
        // Should confirm we checked both
        verify(exactly = 1) { primary.findByUuid(testUuid) }
    }
}
