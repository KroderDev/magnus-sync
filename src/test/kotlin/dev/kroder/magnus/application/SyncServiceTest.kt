package dev.kroder.magnus.application

import dev.kroder.magnus.domain.exception.SessionLockedException
import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.domain.processing.LockManager
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class SyncServiceTest {
    private val repository = mockk<PlayerRepository>(relaxed = true)
    private val lockManager = mockk<LockManager>(relaxed = true)
    private val syncService = SyncService(repository, lockManager)
    private val uuid = UUID.randomUUID()

    @Test
    fun `should throw SessionLockedException when session is locked`() {
        every { lockManager.isLocked(uuid) } returns true

        assertThrows(SessionLockedException::class.java) {
            syncService.loadPlayerData(uuid)
        }
    }

    @Test
    fun `should load data when session is unlocked`() {
        every { lockManager.isLocked(uuid) } returns false
        every { repository.findByUuid(uuid) } returns null

        assertDoesNotThrow {
            syncService.loadPlayerData(uuid)
        }
    }

    @Test
    fun `should lock and unlock session during save`() {
        val data = mockk<PlayerData>()
        every { data.uuid } returns uuid

        syncService.savePlayerData(data)

        verify(exactly = 1) { lockManager.lock(uuid) }
        verify(exactly = 1) { repository.save(data) }
        verify(exactly = 1) { lockManager.unlock(uuid) }
    }
    
    @Test
    fun `should unlock session even if save fails`() {
        val data = mockk<PlayerData>()
        every { data.uuid } returns uuid
        every { repository.save(data) } throws RuntimeException("Save failed")

        assertThrows(RuntimeException::class.java) {
            syncService.savePlayerData(data)
        }

        verify(exactly = 1) { lockManager.lock(uuid) }
        verify(exactly = 1) { lockManager.unlock(uuid) }
    }

    @Test
    fun `should save all players even if some fail`() {
        val player1 = mockk<PlayerData>()
        val player2 = mockk<PlayerData>()
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        
        every { player1.uuid } returns uuid1
        every { player2.uuid } returns uuid2
        
        // player1 fails, player2 succeeds
        every { repository.save(player1) } throws RuntimeException("Connection lost")
        every { repository.save(player2) } just Runs

        syncService.saveAllPlayerData(listOf(player1, player2))

        // Verify both were attempted
        verify(exactly = 1) { repository.save(player1) }
        verify(exactly = 1) { repository.save(player2) }
    }
}
