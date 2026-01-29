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
    val redisPass: String? = null
)
