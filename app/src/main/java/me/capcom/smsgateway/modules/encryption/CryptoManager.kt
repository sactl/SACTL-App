package me.capcom.smsgateway.modules.encryption

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * CryptoManager handles RSA encryption for outbound webhook payloads.
 *
 * This class encrypts SMS content on-device using a user-provided RSA public key
 * before forwarding to webhooks, ensuring the server never sees plaintext.
 *
 * Algorithm: RSA/ECB/OAEPWithSHA-256AndMGF1Padding
 *
 * Usage:
 * - User provides their RSA public key in PEM format via settings
 * - SMS body is encrypted before being sent to webhook
 * - If no public key is configured, messages are DROPPED (fail-safe)
 */
class CryptoManager(
    private val settings: EncryptionSettings,
) {
    companion object {
        private const val RSA_ALGORITHM = "RSA"
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        // RSA with OAEP-SHA256 can encrypt (keySize/8 - 66) bytes
        // For 2048-bit key: 256 - 66 = 190 bytes max
        // For 4096-bit key: 512 - 66 = 446 bytes max
        private const val PEM_HEADER = "-----BEGIN PUBLIC KEY-----"
        private const val PEM_FOOTER = "-----END PUBLIC KEY-----"
    }

    /**
     * Check if encryption is properly configured with a valid public key.
     *
     * @return true if a public key is configured, false otherwise
     */
    fun isConfigured(): Boolean {
        return settings.publicKeyPem?.isNotBlank() == true
    }

    /**
     * Get the configured public key, or null if not set.
     */
    fun getPublicKey(): PublicKey? {
        val pemKey = settings.publicKeyPem ?: return null
        if (pemKey.isBlank()) return null

        return try {
            parsePublicKeyFromPem(pemKey)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypt plaintext using the configured RSA public key.
     * Runs on Dispatchers.IO for optimal performance.
     *
     * @param plaintext The text to encrypt (e.g., SMS body)
     * @return Base64-encoded ciphertext
     * @throws IllegalStateException if no public key is configured
     * @throws CryptoException if encryption fails
     */
    suspend fun encryptAsync(plaintext: String): String = withContext(Dispatchers.IO) {
        val publicKey = getPublicKey()
            ?: throw IllegalStateException("Public key is not configured. Cannot encrypt.")

        encrypt(plaintext, publicKey)
    }

    /**
     * Encrypt plaintext using the configured RSA public key (synchronous version).
     * Prefer encryptAsync() for better performance.
     *
     * @param plaintext The text to encrypt (e.g., SMS body)
     * @return Base64-encoded ciphertext
     * @throws IllegalStateException if no public key is configured
     * @throws CryptoException if encryption fails
     */
    fun encrypt(plaintext: String): String {
        val publicKey = getPublicKey()
            ?: throw IllegalStateException("Public key is not configured. Cannot encrypt.")

        return encrypt(plaintext, publicKey)
    }

    /**
     * Encrypt plaintext using the provided RSA public key.
     *
     * @param plaintext The text to encrypt
     * @param publicKeyPem The RSA public key in PEM format
     * @return Base64-encoded ciphertext
     * @throws CryptoException if encryption fails
     */
    fun encrypt(plaintext: String, publicKeyPem: String): String {
        val publicKey = parsePublicKeyFromPem(publicKeyPem)
        return encrypt(plaintext, publicKey)
    }

    /**
     * Encrypt plaintext using the provided RSA public key.
     *
     * @param plaintext The text to encrypt
     * @param publicKey The RSA public key
     * @return Base64-encoded ciphertext
     * @throws CryptoException if encryption fails
     */
    private fun encrypt(plaintext: String, publicKey: PublicKey): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(plaintextBytes)

            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw CryptoException("Failed to encrypt data: ${e.message}", e)
        }
    }

    /**
     * Parse an RSA public key from PEM format.
     *
     * Accepts keys with or without PEM headers/footers.
     *
     * @param pemKey The public key in PEM format
     * @return The parsed PublicKey object
     * @throws CryptoException if parsing fails
     */
    private fun parsePublicKeyFromPem(pemKey: String): PublicKey {
        return try {
            // Remove PEM headers and whitespace
            val keyContent = pemKey
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                .replace("\\s".toRegex(), "")

            val keyBytes = Base64.decode(keyContent, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)

            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            throw CryptoException("Failed to parse public key: ${e.message}", e)
        }
    }

    /**
     * Validate that a PEM-formatted public key is valid and can be used for encryption.
     *
     * @param pemKey The public key in PEM format
     * @return true if the key is valid, false otherwise
     */
    fun validatePublicKey(pemKey: String): Boolean {
        return try {
            val publicKey = parsePublicKeyFromPem(pemKey)
            // Try a test encryption to validate the key works
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Exception thrown when cryptographic operations fail.
 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
