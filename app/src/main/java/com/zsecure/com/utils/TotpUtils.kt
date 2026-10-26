package com.zsecure.com.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpUtils {

    // MD5 appears in legacy Google Authenticator migration exports.
    val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512", "MD5")

    /**
     * Decodes a Base32 string into a byte array.
     */
    fun decodeBase32(base32: String): ByteArray {
        val cleanBase32 = base32.uppercase().replace(" ", "").replace("-", "")
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var value = 0
        val bytes = mutableListOf<Byte>()

        for (c in cleanBase32) {
            if (c == '=') break
            val digit = base32Chars.indexOf(c)
            if (digit == -1) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }
            value = (value shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bytes.add(((value ushr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return bytes.toByteArray()
    }

    /**
     * Computes a TOTP code honouring the account's own parameters.
     *
     * Previously the algorithm, digit count and time step were hard-coded to
     * HmacSHA1 / 6 digits / 30 s, so accounts issued with SHA-256, 8 digits or
     * a 60 s step silently displayed WRONG codes, and any failure was masked
     * as a plausible-looking "000 000".
     *
     * @return the zero-padded code, or null if the secret/parameters are invalid.
     */
    fun generateTotp(
        secretBase32: String,
        timeMs: Long = System.currentTimeMillis(),
        digits: Int = 6,
        periodSec: Int = 30,
        algorithm: String = "SHA1"
    ): String? {
        if (periodSec <= 0) return null
        return try {
            val secretBytes = decodeBase32(secretBase32)
            if (secretBytes.isEmpty()) return null
            val counter = timeMs / 1000 / periodSec
            generateHotp(secretBytes, counter, digits, algorithm)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Computes an HOTP (counter-based) code, or null on invalid input.
     */
    fun generateHotp(
        secretBase32: String,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String? {
        return try {
            val secretBytes = decodeBase32(secretBase32)
            if (secretBytes.isEmpty()) return null
            generateHotp(secretBytes, counter, digits, algorithm)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHotp(secret: ByteArray, counter: Long, digits: Int, algorithm: String): String {
        require(digits in 6..8) { "digits must be 6..8" }
        val algo = algorithm.uppercase()
        require(algo in SUPPORTED_ALGORITHMS) { "unsupported algorithm: $algorithm" }

        val data = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }

        val hmacAlgorithm = "Hmac$algo"
        val mac = Mac.getInstance(hmacAlgorithm)
        val keySpec = SecretKeySpec(secret, hmacAlgorithm)
        mac.init(keySpec)
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        var modulus = 1
        repeat(digits) { modulus *= 10 }
        val otp = binary % modulus
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Display formatting: 6-digit codes are grouped "123 456"; other lengths
     * are shown grouped in the middle where sensible (8 -> "1234 5678").
     */
    fun formatCode(code: String): String {
        return if (code.length % 2 == 0 && code.length >= 6) {
            val half = code.length / 2
            "${code.substring(0, half)} ${code.substring(half)}"
        } else {
            code
        }
    }
}
