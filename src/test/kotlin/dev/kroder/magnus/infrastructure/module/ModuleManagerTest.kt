package dev.kroder.magnus.infrastructure.module

import dev.kroder.magnus.domain.module.MagnusModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleManagerTest {
    private lateinit var moduleManager: ModuleManager
    private lateinit var mockModule: MagnusModule

    @BeforeEach
    fun setup() {
        moduleManager = ModuleManager()
        mockModule = mockk(relaxed = true)
        every { mockModule.id } returns "test-module"
        every { mockModule.name } returns "Test Module"
    }

    @Test
    fun `should register and enable module`() {
        moduleManager.registerModule(mockModule)
        moduleManager.enableModule("test-module")

        assertTrue(moduleManager.isModuleActive("test-module"))
        verify(exactly = 1) { mockModule.onEnable() }
    }

    @Test
    fun `should not enable module if not registered`() {
        moduleManager.enableModule("non-existent")
        assertFalse(moduleManager.isModuleActive("non-existent"))
    }

    @Test
    fun `should not enable module twice`() {
        moduleManager.registerModule(mockModule)
        moduleManager.enableModule("test-module")
        moduleManager.enableModule("test-module")

        assertTrue(moduleManager.isModuleActive("test-module"))
        verify(exactly = 1) { mockModule.onEnable() }
    }

    @Test
    fun `should disable active module`() {
        moduleManager.registerModule(mockModule)
        moduleManager.enableModule("test-module")
        moduleManager.disableModule("test-module")

        assertFalse(moduleManager.isModuleActive("test-module"))
        verify(exactly = 1) { mockModule.onDisable() }
    }

    @Test
    fun `should handle error during onEnable gracefully`() {
        every { mockModule.onEnable() } throws RuntimeException("Enable failed")
        
        moduleManager.registerModule(mockModule)
        moduleManager.enableModule("test-module")

        assertFalse(moduleManager.isModuleActive("test-module"))
    }

    @Test
    fun `should shutdown all modules`() {
        val secondModule = mockk<MagnusModule>(relaxed = true)
        every { secondModule.id } returns "second-module"
        every { secondModule.name } returns "Second Module"

        moduleManager.registerModule(mockModule)
        moduleManager.registerModule(secondModule)
        
        moduleManager.enableModule("test-module")
        moduleManager.enableModule("second-module")

        moduleManager.shutdown()

        assertFalse(moduleManager.isModuleActive("test-module"))
        assertFalse(moduleManager.isModuleActive("second-module"))
        verify { mockModule.onDisable() }
        verify { secondModule.onDisable() }
    }
}
