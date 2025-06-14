package com.globlink.vpn.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DecryptedConfig(
    val timestamp: Long,
    val vpnId: String,
    val config: String
)

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val SECRET_KEY = "1234567812345678"
    private val ZERO_IV = ByteArray(16) { 0 } // 16 bytes of zeros

    fun decryptConfig(encryptedConfig: String): DecryptedConfig? {
        return try {
            val decrypted = decryptAES(encryptedConfig)
            if (decrypted.length < 12) return null // Minimum length for timestamp + length

            // Extract timestamp (first 10 digits)
            val timestamp = decrypted.substring(0, 10).toLongOrNull() ?: return null

            // Extract VPN ID length (next 2 digits)
            val vpnIdLength = decrypted.substring(10, 12).toIntOrNull() ?: return null
            if (decrypted.length < 12 + vpnIdLength) return null

            // Extract VPN ID
            val vpnId = decrypted.substring(12, 12 + vpnIdLength)

            // Extract actual config
            val config = decrypted.substring(12 + vpnIdLength)

            DecryptedConfig(timestamp, vpnId, config)
        } catch (e: Exception) {
            null
        }
    }

    fun isConfigValid(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis() / 1000 // Convert to seconds
        val timeDiff = Math.abs(currentTime - timestamp)
        return timeDiff <= 300 // Within 5 minutes
    }

    fun decryptAES(encryptedText: String): String {
        return try {
            val key = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            val ivSpec = IvParameterSpec(ZERO_IV)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            
            val encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            // Add @ prefix to match the website format
            String(decryptedBytes, Charsets.UTF_8)
            
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedText // Return original text if decryption fails
        }
    }

    fun isEncrypted(text: String): Boolean {
        return try {
            Base64.decode(text, Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }
} 