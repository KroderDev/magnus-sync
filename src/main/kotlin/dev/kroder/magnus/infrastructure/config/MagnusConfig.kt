package dev.kroder.magnus.infrastructure.config

/**
 * Configuration for the Magnus Sync mod.
 * In a production environment, these values should be loaded from a .json or .toml file.
 * 
 * Functional Limits:
 * - This is a simple data holding class.
 * - Connection pooling settings are fixed for now.
 */
import kotlinx.serialization.Serializable

@Serializable
data class MagnusConfig(
    val postgresUrl: String = "jdbc:postgresql://localhost:5432/magnus",
    val postgresUser: String = "postgres",
    val postgresPass: String = "password",
    val redisHost: String = "localhost",
    val redisPort: Int = 6379,
    val redisPass: String? = null,
    
    // Server Identification
    val serverName: String = "default",
    
    // Module Feature Flags
    val enableInventorySync: Boolean = true,
    val enableGlobalChat: Boolean = false,
    val enableGlobalPlayerList: Boolean = false,
    val enableSessionLock: Boolean = false,
    
    // Redis Security Settings
    val redisSsl: Boolean = false,
    val enableMessageSigning: Boolean = true,  // Secure by default
    val messageSigningSecret: String? = null,
    val maxMessageSizeBytes: Int = 65536,  // 64KB limit
    
    // Redis Resilience Settings
    val subscriptionRetryDelayMs: Long = 5000,
    val maxSubscriptionRetries: Int = 10
)
