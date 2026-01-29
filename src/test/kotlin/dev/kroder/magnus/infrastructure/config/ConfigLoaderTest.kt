package dev.kroder.magnus.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ConfigLoaderTest {

    @Test
    fun `should create default config if file does not exist`(@TempDir tempDir: Path) {
        // Given
        val expectedFile = tempDir.resolve("magnus.json")

        // When
        val config = ConfigLoader.load(tempDir)

        // Then
        assertTrue(expectedFile.exists())
        assertEquals("jdbc:postgresql://localhost:5432/magnus", config.postgresUrl)
    }

    @Test
    fun `should load existing config`(@TempDir tempDir: Path) {
        // Given
        val configFile = tempDir.resolve("magnus.json")
        configFile.writeText("""
            {
                "postgresUrl": "jdbc:postgresql://remote:5432/magnus_prod",
                "postgresUser": "admin",
                "redisHost": "cache"
            }
        """.trimIndent())

        // When
        val config = ConfigLoader.load(tempDir)

        // Then
        assertEquals("jdbc:postgresql://remote:5432/magnus_prod", config.postgresUrl)
        assertEquals("admin", config.postgresUser)
        assertEquals("cache", config.redisHost)
        assertEquals(6379, config.redisPort) // Default val
    }

    @Test
    fun `should return default config on corruption but NOT overwrite`(@TempDir tempDir: Path) {
        // Given
        val configFile = tempDir.resolve("magnus.json")
        val brokenContent = "{ broken json }"
        configFile.writeText(brokenContent)

        // When
        val config = ConfigLoader.load(tempDir)

        // Then
        // Returns default object
        assertEquals("jdbc:postgresql://localhost:5432/magnus", config.postgresUrl)
        
        // File content should remain broken (user needs to fix it manually)
        assertEquals(brokenContent, configFile.readText())
    }
}
