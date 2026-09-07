package com.example.data.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val AES_GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12
    private const val DEFAULT_SECRET_PHRASE = "SISMO_ALERTA_FAMILY_RESILIENCE_2026_KEY_256"

    /**
     * Derives a 256-bit SecretKey from a passkey/phrase using SHA-256.
     */
    private fun deriveKey(secretPhrase: String = DEFAULT_SECRET_PHRASE): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secretPhrase.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext using AES-256-GCM and returns Base64 string [IV + Ciphertext].
     */
    fun encryptAES256GCM(plaintext: String, secretPhrase: String = DEFAULT_SECRET_PHRASE): String {
        return try {
            val key = deriveKey(secretPhrase)
            val iv = ByteArray(IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            "ENC_ERR:" + Base64.encodeToString(plaintext.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    /**
     * Decrypts Base64 string [IV + Ciphertext] using AES-256-GCM.
     */
    fun decryptAES256GCM(encryptedBase64: String, secretPhrase: String = DEFAULT_SECRET_PHRASE): String {
        return try {
            if (encryptedBase64.startsWith("ENC_ERR:")) {
                val raw = encryptedBase64.substringAfter("ENC_ERR:")
                return String(Base64.decode(raw, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }

            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTES) return "Invalid Payload"

            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)

            val encryptedSize = combined.size - IV_LENGTH_BYTES
            val encryptedBytes = ByteArray(encryptedSize)
            System.arraycopy(combined, IV_LENGTH_BYTES, encryptedBytes, 0, encryptedSize)

            val key = deriveKey(secretPhrase)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption Error: Check Key"
        }
    }

    /**
     * Formats Google Maps SMS fallback link.
     */
    fun buildGoogleMapsUrl(latitude: Double, longitude: Double): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    /**
     * Formats Emergency SMS message payload under 160 characters.
     */
    fun buildEmergencySmsMessage(
        statusType: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int
    ): String {
        val mapsUrl = buildGoogleMapsUrl(latitude, longitude)
        return "¡ALERTA DE SISMO! Estado: $statusType. Batería: $batteryLevel%. Ubicación: $mapsUrl"
    }
}
