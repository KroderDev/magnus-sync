package dev.kroder.magnus.infrastructure.messaging

import dev.kroder.magnus.domain.messaging.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis-based implementation of the MessageBus.
 * Uses a separate thread for subscription listening.
 */
class RedisMessageBus(private val jedisPool: JedisPool) : MessageBus {
    private val logger = LoggerFactory.getLogger("magnus-redis-bus")
    private val listeners = ConcurrentHashMap<String, JedisPubSub>()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun publish(channel: String, message: String) {
        scope.launch {
            try {
                jedisPool.resource.use { jedis ->
                    jedis.publish(channel, message)
                }
            } catch (e: Exception) {
                logger.error("Failed to publish message to channel '$channel': ${e.message}", e)
            }
        }
    }

    override fun subscribe(channel: String, callback: (String) -> Unit) {
        if (listeners.containsKey(channel)) {
            logger.warn("Already subscribed to channel '$channel'!")
            return
        }

        val pubSub = object : JedisPubSub() {
            override fun onMessage(ch: String, msg: String) {
                try {
                    callback(msg)
                } catch (e: Exception) {
                    logger.error("Error handling message on channel '$ch': ${e.message}", e)
                }
            }
        }

        listeners[channel] = pubSub

        // Subscribe in a separate thread as it blocks
        Thread {
            try {
                jedisPool.resource.use { jedis ->
                    logger.info("Subscribing to channel: $channel")
                    jedis.subscribe(pubSub, channel)
                }
            } catch (e: Exception) {
                logger.error("Subscription to '$channel' failed or was interrupted: ${e.message}")
            } finally {
                listeners.remove(channel)
            }
        }.start()
    }

    override fun close() {
        logger.info("Closing all Redis subscriptions...")
        listeners.values.forEach { 
            try {
                if (it.isSubscribed) it.unsubscribe()
            } catch (e: Exception) {
                logger.error("Error unsubscribing: ${e.message}")
            }
        }
        listeners.clear()
    }
}
