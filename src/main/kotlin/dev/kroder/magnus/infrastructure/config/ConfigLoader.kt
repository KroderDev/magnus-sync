package dev.kroder.magnus.infrastructure.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Base64

object ConfigLoader {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(configDir: java.nio.file.Path? = null): MagnusConfig {
        val dir = configDir ?: FabricLoader.getInstance().configDir
        val configFile = dir.resolve("magnus.json").toFile()

        if (!configFile.exists()) {
            val defaultConfig = createSecureDefaultConfig()
            save(defaultConfig, configFile)
            return defaultConfig
        }

        return try {
            val content = Files.readString(configFile.toPath())
            val config = json.decodeFromString(MagnusConfig.serializer(), content)
            
            // Auto-generate signing secret if signing is enabled but no secret exists
            if (config.enableMessageSigning && config.messageSigningSecret.isNullOrEmpty()) {
                val secureConfig = config.copy(messageSigningSecret = generateSecureSecret())
                save(secureConfig, configFile)
                secureConfig
            } else {
                config
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to default if corrupted, but maybe better to crash or warn?
            // For now, return default but don't overwrite the corrupt file so user can fix it.
            MagnusConfig() 
        }
    }
    
    /**
     * Creates a default config with a pre-generated signing secret.
     * This ensures "secure by default" - new installations have signing enabled.
     */
    private fun createSecureDefaultConfig(): MagnusConfig {
        return MagnusConfig(
            messageSigningSecret = generateSecureSecret()
        )
    }
    
    /**
     * Generates a cryptographically secure random secret (32 bytes, Base64 encoded).
     * This is suitable for HMAC-SHA256 signing.
     */
    private fun generateSecureSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
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

