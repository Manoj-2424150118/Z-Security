package com.zsecure.com.utils

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpUtils {

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
     * Computes the standard 2FA TOTP code for a secret key.
     * @param secretBase32 The Base32-encoded secret.
     * @param timeMs The epoch time in milliseconds (defaults to current time).
     * @param timeStepSec The time step in seconds (defaults to 30).
     */
    fun generateTotp(secretBase32: String, timeMs: Long = System.currentTimeMillis(), timeStepSec: Int = 30): String {
        return try {
            val secretBytes = decodeBase32(secretBase32)
            val counter = timeMs / 1000 / timeStepSec
            val code = getHotpCode(secretBytes, counter)
            // Format as "123 456"
            "${code.substring(0, 3)} ${code.substring(3)}"
        } catch (e: Exception) {
            "000 000"
        }
    }

    private fun getHotpCode(secret: ByteArray, counter: Long): String {
        val data = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        val keySpec = SecretKeySpec(secret, "RAW")
        mac.init(keySpec)
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1000000
        return otp.toString().padStart(6, '0')
    }
}
