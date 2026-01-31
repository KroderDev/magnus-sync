package dev.kroder.magnus.infrastructure.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageSignerTest {
    
    private val secret = "test-secret-32-bytes-minimum!!"
    private val signer = MessageSigner(secret)
    
    @Test
    fun `should sign and verify message successfully`() {
        val originalMessage = """{"test": "hello world"}"""
        
        val signed = signer.sign(originalMessage)
        val verified = signer.verify(signed)
        
        assertEquals(originalMessage, verified)
    }
    
    @Test
    fun `should reject message with invalid signature`() {
        val originalMessage = """{"test": "hello world"}"""
        val signed = signer.sign(originalMessage)
        
        // Tamper with the signature
        val tampered = "INVALID" + signed.substring(7)
        
        val verified = signer.verify(tampered)
        
        assertNull(verified)
    }
    
    @Test
    fun `should reject message with tampered payload`() {
        val originalMessage = """{"test": "hello world"}"""
        val signed = signer.sign(originalMessage)
        
        // Split and tamper with payload
        val parts = signed.split("|", limit = 3)
        val tamperedPayload = """{"test": "HACKED!"}"""
        val tampered = "${parts[0]}|${parts[1]}|$tamperedPayload"
        
        val verified = signer.verify(tampered)
        
        assertNull(verified)
    }
    
    @Test
    fun `should reject expired message`() {
        val originalMessage = """{"test": "hello world"}"""
        
        // Create a message with old timestamp manually
        val oldTimestamp = System.currentTimeMillis() - 60_000  // 60 seconds old
        val dataToSign = "$oldTimestamp|$originalMessage"
        
        // Use reflection to access private method for testing
        val signMethod = MessageSigner::class.java.getDeclaredMethod("computeHmac", String::class.java)
        signMethod.isAccessible = true
        val signature = signMethod.invoke(signer, dataToSign) as String
        
        val oldMessage = "$signature|$dataToSign"
        
        // Should be rejected due to age (default 30s max)
        val verified = signer.verify(oldMessage)
        
        assertNull(verified)
    }
    
    @Test
    fun `should accept message within time window`() {
        val originalMessage = """{"test": "hello world"}"""
        val signed = signer.sign(originalMessage)
        
        // Immediately verify (within 30 seconds)
        val verified = signer.verify(signed, maxAgeMs = 30_000)
        
        assertEquals(originalMessage, verified)
    }
    
    @Test
    fun `should reject malformed message format`() {
        // Missing parts
        assertNull(signer.verify("only-one-part"))
        assertNull(signer.verify("two|parts"))
    }
    
    @Test
    fun `different signers with different secrets should not verify each other`() {
        val signer1 = MessageSigner("secret-one-32-bytes-minimum!!!")
        val signer2 = MessageSigner("secret-two-32-bytes-minimum!!!")
        
        val originalMessage = """{"test": "cross verification"}"""
        val signed = signer1.sign(originalMessage)
        
        // signer2 should not be able to verify signer1's message
        val verified = signer2.verify(signed)
        
        assertNull(verified)
    }
}
