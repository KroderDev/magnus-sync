package dev.kroder.magnus.infrastructure.messaging

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.io.FileInputStream
import java.security.KeyStore

/**
 * Factory for creating JedisPool instances with optional SSL/TLS support.
 * 
 * Security Features:
 * - SSL/TLS encryption support
 * - Custom truststore for certificate verification
 * - Connection pooling with health checks
 */
object JedisPoolFactory {
    
    private val logger = LoggerFactory.getLogger("magnus-jedis-factory")
    
    /**
     * Creates a JedisPool with the specified configuration.
     * 
     * @param host Redis server hostname
     * @param port Redis server port
     * @param password Redis password (optional)
     * @param useSsl Whether to use SSL/TLS encryption
     * @param truststorePath Path to JKS truststore file (optional, uses system default if null)
     * @param truststorePassword Password for the truststore
     * @param timeoutMs Connection timeout in milliseconds
     * @return Configured JedisPool instance
     */
    fun create(
        host: String,
        port: Int,
        password: String? = null,
        useSsl: Boolean = false,
        truststorePath: String? = null,
        truststorePassword: String = "changeit",
        timeoutMs: Int = 2000
    ): JedisPool {
        val config = JedisPoolConfig().apply {
            maxTotal = 16
            maxIdle = 8
            minIdle = 2
            testOnBorrow = true
            testWhileIdle = true
            // Block for at most 5 seconds when pool is exhausted
            blockWhenExhausted = true
            setMaxWait(java.time.Duration.ofSeconds(5))
        }
        
        return if (useSsl) {
            logger.info("Creating SSL-enabled JedisPool for $host:$port")
            
            val sslContext = if (truststorePath != null) {
                logger.info("Using custom truststore: $truststorePath")
                createSslContext(truststorePath, truststorePassword)
            } else {
                SSLContext.getDefault()
            }
            
            JedisPool(
                config,
                host,
                port,
                timeoutMs,
                password,
                /* database */ 0,
                /* clientName */ "magnus",
                useSsl,
                sslContext.socketFactory,
                sslContext.defaultSSLParameters,
                /* hostnameVerifier */ null
            )
        } else {
            logger.info("Creating JedisPool for $host:$port (SSL disabled)")
            
            if (password.isNullOrEmpty()) {
                JedisPool(config, host, port, timeoutMs)
            } else {
                JedisPool(config, host, port, timeoutMs, password)
            }
        }
    }
    
    private fun createSslContext(truststorePath: String, password: String): SSLContext {
        val trustStore = KeyStore.getInstance("JKS")
        FileInputStream(truststorePath).use { fis ->
            trustStore.load(fis, password.toCharArray())
        }
        
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)
        
        return SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, null)
        }
    }
}
