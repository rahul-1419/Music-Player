package com.example.extractor

import com.example.model.AudioQuality
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SaavnDecryptor {

    private const val DES_KEY = "38346591"

    /**
     * Decrypts JioSaavn encrypted_media_url into the base media stream URL.
     * Uses DES/ECB/PKCS5Padding with key "38346591".
     * Works seamlessly on both Android devices and JVM unit tests.
     */
    fun decryptMediaUrl(encryptedUrl: String): String? {
        if (encryptedUrl.isBlank()) return null
        return try {
            val keyBytes = DES_KEY.toByteArray(StandardCharsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val rawBytes = decodeBase64(encryptedUrl) ?: return null
            val decryptedBytes = cipher.doFinal(rawBytes)
            val decryptedUrl = String(decryptedBytes, StandardCharsets.UTF_8).trim()

            // Normalize URL protocol to https
            if (decryptedUrl.startsWith("http://")) {
                "https://" + decryptedUrl.substring(7)
            } else {
                decryptedUrl
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Builds all 4 audio stream quality URLs for a given decrypted media stream URL.
     * Replaces `_96.mp4` suffix with `_320.mp4`, `_160.mp4`, `_96.mp4`, and `_48.mp4`.
     */
    fun buildQualityStreams(baseStreamUrl: String): Map<AudioQuality, String> {
        if (baseStreamUrl.isBlank()) return emptyMap()

        val map = mutableMapOf<AudioQuality, String>()

        // Patterns typically end in _96.mp4, _160.mp4, _320.mp4, or .mp4
        val regex = Regex("_[0-9]+\\.mp4$")

        for (quality in AudioQuality.entries) {
            val streamUrl = if (regex.containsMatchIn(baseStreamUrl)) {
                baseStreamUrl.replace(regex, quality.fileSuffix)
            } else if (baseStreamUrl.endsWith(".mp4")) {
                baseStreamUrl.removeSuffix(".mp4") + quality.fileSuffix
            } else {
                baseStreamUrl
            }
            map[quality] = streamUrl
        }

        return map
    }

    private fun decodeBase64(input: String): ByteArray? {
        // Try java.util.Base64 first (standard in Java 8+ and modern Android)
        return try {
            java.util.Base64.getDecoder().decode(input)
        } catch (_: Throwable) {
            try {
                android.util.Base64.decode(input, android.util.Base64.DEFAULT)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
