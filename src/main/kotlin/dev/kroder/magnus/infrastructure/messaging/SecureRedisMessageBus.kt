package dev.kroder.magnus.infrastructure.messaging

import dev.kroder.magnus.domain.messaging.MessageBus
import dev.kroder.magnus.infrastructure.security.MessageSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardened Redis MessageBus with security and resilience features.
 * 
 * Security Features:
 * - HMAC message signing (optional) - prevents message injection
 * - Payload size limits - prevents DoS via large payloads
 * 
 * Resilience Features:
 * - Automatic subscription recovery with exponential backoff
 * - Proper shutdown handling
 * - Named daemon threads for better debugging
 */
class SecureRedisMessageBus(
    private val jedisPool: JedisPool,
    private val signer: MessageSigner? = null,
    private val maxPayloadSize: Int = 65536,
    private val retryDelayMs: Long = 5000,
    private val maxRetries: Int = 10
) : MessageBus {
    
    private val logger = LoggerFactory.getLogger("magnus-redis-bus")
    private val listeners = ConcurrentHashMap<String, ManagedSubscription>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val isShuttingDown = AtomicBoolean(false)
    
    override fun publish(channel: String, message: String) {
        // Size check before sending
        if (message.length > maxPayloadSize) {
            logger.warn("Rejecting oversized message for channel '$channel': ${message.length} bytes (max: $maxPayloadSize)")
            return
        }
        
        scope.launch {
            try {
                val payload = signer?.sign(message) ?: message
                jedisPool.resource.use { jedis ->
                    jedis.publish(channel, payload)
                }
            } catch (e: Exception) {
                logger.error("Failed to publish to '$channel': ${e.message}", e)
            }
        }
    }
    
    override fun subscribe(channel: String, callback: (String) -> Unit) {
        if (listeners.containsKey(channel)) {
            logger.warn("Already subscribed to channel '$channel'!")
            return
        }
        
        val subscription = ManagedSubscription(channel, callback)
        listeners[channel] = subscription
        launchSubscription(subscription, 0)
    }
    
    private fun launchSubscription(subscription: ManagedSubscription, attempt: Int) {
        if (isShuttingDown.get()) return
        
        val pubSub = object : JedisPubSub() {
            override fun onMessage(ch: String, msg: String) {
                try {
                    // Size check before processing
                    if (msg.length > maxPayloadSize) {
                        logger.warn("Dropping oversized message on '$ch': ${msg.length} bytes (max: $maxPayloadSize)")
                        return
                    }
                    
                    // Signature verification if enabled
                    val payload = if (signer != null) {
                        signer.verify(msg) ?: run {
                            logger.warn("Dropping message with invalid/expired signature on '$ch'")
                            return
                        }
                    } else {
                        msg
                    }
                    
                    subscription.callback(payload)
                } catch (e: Exception) {
                    logger.error("Error handling message on '$ch': ${e.message}", e)
                }
            }
        }
        
        subscription.pubSub = pubSub
        
        Thread {
            try {
                jedisPool.resource.use { jedis ->
                    logger.info("Subscribing to channel: ${subscription.channel}")
                    jedis.subscribe(pubSub, subscription.channel)
                }
            } catch (e: Exception) {
                if (!isShuttingDown.get()) {
                    handleSubscriptionFailure(subscription, attempt, e)
                }
            }
        }.apply {
            name = "magnus-redis-sub-${subscription.channel}"
            isDaemon = true
        }.start()
    }
    
    private fun handleSubscriptionFailure(subscription: ManagedSubscription, attempt: Int, e: Exception) {
        val nextAttempt = attempt + 1
        
        if (nextAttempt > maxRetries) {
            logger.error("Max retries ($maxRetries) exceeded for channel '${subscription.channel}'. Giving up.")
            listeners.remove(subscription.channel)
            return
        }
        
        // Exponential backoff: 5s, 10s, 20s, 40s, 80s, 160s (capped at 32x base delay)
        val delay = retryDelayMs * (1 shl minOf(attempt, 5))
        logger.warn("Subscription to '${subscription.channel}' failed (attempt $nextAttempt/$maxRetries). " +
                    "Retrying in ${delay}ms. Error: ${e.message}")
        
        scope.launch {
            delay(delay)
            launchSubscription(subscription, nextAttempt)
        }
    }
    
    override fun close() {
        isShuttingDown.set(true)
        logger.info("Closing all Redis subscriptions...")
        
        listeners.values.forEach { subscription ->
            try {
                subscription.pubSub?.let {
                    if (it.isSubscribed) it.unsubscribe()
                }
            } catch (e: Exception) {
                logger.error("Error unsubscribing from '${subscription.channel}': ${e.message}")
            }
        }
        listeners.clear()
    }
    
    private class ManagedSubscription(
        val channel: String,
        val callback: (String) -> Unit,
        var pubSub: JedisPubSub? = null
    )
}
