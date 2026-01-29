package dev.kroder.magnus.application

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class BackupRecoveryServiceTest {

    private val localBackup = mockk<LocalBackupRepository>(relaxed = true)
    private val primaryRepo = mockk<PlayerRepository>(relaxed = true)
    private val service = BackupRecoveryService(localBackup, primaryRepo)

    private val testUuid = UUID.randomUUID()
    private val backupData = PlayerData(
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
        lastUpdated = 2000L
    )

    @Test
    fun `should recover backup if primary is empty`() {
        // Given
        every { localBackup.findAllStartups() } returns listOf(backupData)
        every { primaryRepo.findByUuid(testUuid) } returns null

        // When
        service.processBackups()

        // Then
        verify(exactly = 1) { primaryRepo.save(backupData) }
        verify(exactly = 1) { localBackup.deleteFile(testUuid) }
    }

    @Test
    fun `should recover backup if it is fresher than primary`() {
        // Given
        val oldDbData = backupData.copy(lastUpdated = 1000L)
        every { localBackup.findAllStartups() } returns listOf(backupData)
        every { primaryRepo.findByUuid(testUuid) } returns oldDbData

        // When
        service.processBackups()

        // Then
        verify(exactly = 1) { primaryRepo.save(backupData) }
        verify(exactly = 1) { localBackup.deleteFile(testUuid) }
    }

    @Test
    fun `should discard backup if primary is fresher`() {
        // Given
        val newDbData = backupData.copy(lastUpdated = 3000L) // Newer than 2000L
        every { localBackup.findAllStartups() } returns listOf(backupData)
        every { primaryRepo.findByUuid(testUuid) } returns newDbData

        // When
        service.processBackups()

        // Then
        verify(exactly = 0) { primaryRepo.save(any()) }
        verify(exactly = 1) { localBackup.deleteFile(testUuid) }
    }

    @Test
    fun `should skip recovery if primary throws exception`() {
        // Given
        every { localBackup.findAllStartups() } returns listOf(backupData)
        every { primaryRepo.findByUuid(testUuid) } throws RuntimeException("DB Connection Failed")

        // When
        service.processBackups()

        // Then
        verify(exactly = 0) { primaryRepo.save(any()) }
        verify(exactly = 0) { localBackup.deleteFile(any()) }
    }
}
