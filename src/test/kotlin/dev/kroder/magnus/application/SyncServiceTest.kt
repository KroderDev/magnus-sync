package dev.kroder.magnus.application

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class SyncServiceTest {

    private val repository = mockk<PlayerRepository>(relaxed = true)
    private val service = SyncService(repository)

    @Test
    fun `should delegate load to repository`() {
        val uuid = UUID.randomUUID()
        val data = mockk<PlayerData>()
        every { repository.findByUuid(uuid) } returns data

        val result = service.loadPlayerData(uuid)

        assertEquals(data, result)
        verify(exactly = 1) { repository.findByUuid(uuid) }
    }

    @Test
    fun `should delegate save to repository`() {
        val data = mockk<PlayerData>(relaxed = true)
        
        service.savePlayerData(data)

        verify(exactly = 1) { repository.save(data) }
    }

    @Test
    fun `should delegate cache release to repository`() {
        val uuid = UUID.randomUUID()

        service.releaseCache(uuid)

        verify(exactly = 1) { repository.deleteCache(uuid) }
    }
}
