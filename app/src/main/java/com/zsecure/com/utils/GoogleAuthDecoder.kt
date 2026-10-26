package com.zsecure.com.utils

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Decodes Google Authenticator migration payloads (otpauth-migration://).
 *
 * Hardened: the previous decoder trusted protobuf length prefixes blindly
 * (ByteArray(len) with no cap and unchecked read() calls), so a crafted QR
 * could force a huge allocation (OOM/crash) or silently zero-pad short
 * reads. Lengths are now validated against the bytes actually available,
 * reads are exact, and varints are bounded to 10 bytes.
 *
 * Also parses the fields the old decoder dropped: algorithm (4), digits (5),
 * account type (6) and HOTP counter (7) — without them, SHA-256 / 8-digit /
 * HOTP accounts were imported as wrong SHA1/6-digit TOTPs.
 */
object GoogleAuthDecoder {
    fun decodeMigration(payload: ByteArray): List<Map<String, String>> {
        val accounts = mutableListOf<Map<String, String>>()
        val bis = ByteArrayInputStream(payload)

        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()

            if (tag == 1 && wireType == 2) {
                val subBytes = readLengthDelimited(bis)
                val acc = parseOtpParameters(subBytes)
                if (acc != null) {
                    accounts.add(acc)
                }
            } else {
                skipField(bis, wireType)
            }
        }
        return accounts
    }

    private fun parseOtpParameters(bytes: ByteArray): Map<String, String>? {
        val bis = ByteArrayInputStream(bytes)
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = "SHA1"
        var digits = "6"
        var type = "TOTP"
        var counter = "0"

        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()

            when {
                tag == 1 && wireType == 2 -> secret = readLengthDelimited(bis)
                tag == 2 && wireType == 2 -> name = String(readLengthDelimited(bis), Charsets.UTF_8)
                tag == 3 && wireType == 2 -> issuer = String(readLengthDelimited(bis), Charsets.UTF_8)
                tag == 4 && wireType == 0 -> {
                    algorithm = when (readVarint(bis)) {
                        2L -> "SHA256"
                        3L -> "MD5" // unsupported by TotpUtils; import keeps the label and code gen fails visibly
                        else -> "SHA1" // 0 = unspecified, 1 = SHA1
                    }
                }
                tag == 5 && wireType == 0 -> {
                    digits = when (readVarint(bis)) {
                        2L -> "8" // EIGHT
                        else -> "6" // unspecified / SIX
                    }
                }
                tag == 6 && wireType == 0 -> {
                    type = when (readVarint(bis)) {
                        1L -> "HOTP"
                        else -> "TOTP" // unspecified / TOTP
                    }
                }
                tag == 7 && wireType == 0 -> counter = readVarint(bis).toString()
                else -> skipField(bis, wireType)
            }
        }

        if (secret == null) return null

        return mapOf(
            "secret" to encodeBase32(secret),
            "name" to name,
            "issuer" to if (issuer.isEmpty()) "Service" else issuer,
            "algorithm" to algorithm,
            "digits" to digits,
            "type" to type,
            "counter" to counter
        )
    }

    /** Reads a length-delimited field with strict bounds checking. */
    private fun readLengthDelimited(input: ByteArrayInputStream): ByteArray {
        val len = readVarint(input)
        if (len < 0 || len > input.available()) {
            throw EOFException("Declared length $len exceeds available ${input.available()}")
        }
        val bytes = ByteArray(len.toInt())
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n == -1) throw EOFException("Unexpected end of stream")
            read += n
        }
        return bytes
    }

    private fun readVarint(input: InputStream): Long {
        var value = 0L
        var shift = 0
        while (true) {
            if (shift >= 64) throw EOFException("Varint too long")
            val b = input.read()
            if (b == -1) throw EOFException()
            value = value or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }

    private fun skipField(input: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(input)
            1 -> skipFully(input, 8)
            2 -> {
                val len = readVarint(input)
                if (len < 0 || len > Int.MAX_VALUE) throw EOFException("Invalid length $len")
                skipFully(input, len)
            }
            5 -> skipFully(input, 4)
            else -> throw EOFException("Unsupported wire type $wireType")
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) {
                if (input.read() == -1) throw EOFException("Unexpected end of stream while skipping")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun encodeBase32(bytes: ByteArray): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var i = 0
        var index = 0
        var digit: Int
        var currByte: Int
        var nextByte: Int

        while (i < bytes.size) {
            currByte = if (bytes[i] >= 0) bytes[i].toInt() else bytes[i].toInt() + 256
            if (index > 3) {
                nextByte = if (i + 1 < bytes.size) {
                    if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else bytes[i + 1].toInt() + 256
                } else 0
                digit = currByte and (0xFF ushr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte ushr (8 - index))
                i++
            } else {
                digit = (currByte ushr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            sb.append(base32Chars[digit])
        }
        return sb.toString()
    }
}
