package dev.kroder.magnus.domain.exception

/**
 * Exception thrown when the player data cannot be retrieved from any source.
 * This usually means the database and cache are both down, or the data is corrupted.
 */
class DataUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
