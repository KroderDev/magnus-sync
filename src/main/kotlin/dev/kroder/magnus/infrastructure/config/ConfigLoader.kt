package dev.kroder.magnus.infrastructure.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files

object ConfigLoader {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(configDir: java.nio.file.Path? = null): MagnusConfig {
        val dir = configDir ?: FabricLoader.getInstance().configDir
        val configFile = dir.resolve("magnus.json").toFile()

        if (!configFile.exists()) {
            val defaultConfig = MagnusConfig()
            save(defaultConfig, configFile)
            return defaultConfig
        }

        return try {
            val content = Files.readString(configFile.toPath())
            json.decodeFromString(MagnusConfig.serializer(), content)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to default if corrupted, but maybe better to crash or warn?
            // For now, return default but don't overwrite the corrupt file so user can fix it.
            MagnusConfig() 
        }
    }

    private fun save(config: MagnusConfig, configFile: File) {
        try {
            val content = json.encodeToString(MagnusConfig.serializer(), config)
            Files.writeString(configFile.toPath(), content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
