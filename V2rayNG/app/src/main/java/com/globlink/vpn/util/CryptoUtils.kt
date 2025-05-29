package com.globlink.vpn.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val SECRET_KEY = "1234567812345678"
    private val ZERO_IV = ByteArray(16) { 0 } // 16 bytes of zeros

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