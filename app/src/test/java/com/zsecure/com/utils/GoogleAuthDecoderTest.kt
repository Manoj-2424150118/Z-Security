package com.zsecure.com.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException

/** Builds synthetic Google Authenticator migration payloads and decodes them. */
class GoogleAuthDecoderTest {

    private fun varint(v: Long): ByteArray {
        var value = v
        val out = ByteArrayOutputStream()
        while (true) {
            val b = (value and 0x7F).toInt()
            value = value ushr 7
            if (value == 0L) {
                out.write(b)
                break
            }
            out.write(b or 0x80)
        }
        return out.toByteArray()
    }

    private fun fieldBytes(tag: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varint((tag shl 3 or 2).toLong()))
        out.write(varint(payload.size.toLong()))
        out.write(payload)
        return out.toByteArray()
    }

    private fun fieldVarint(tag: Int, v: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varint((tag shl 3 or 0).toLong()))
        out.write(varint(v))
        return out.toByteArray()
    }

    private fun otpParameters(
        secret: ByteArray,
        name: String,
        issuer: String,
        algorithm: Long? = null,
        digits: Long? = null,
        type: Long? = null,
        counter: Long? = null
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(fieldBytes(1, secret))
        out.write(fieldBytes(2, name.toByteArray(Charsets.UTF_8)))
        out.write(fieldBytes(3, issuer.toByteArray(Charsets.UTF_8)))
        if (algorithm != null) out.write(fieldVarint(4, algorithm))
        if (digits != null) out.write(fieldVarint(5, digits))
        if (type != null) out.write(fieldVarint(6, type))
        if (counter != null) out.write(fieldVarint(7, counter))
        return out.toByteArray()
    }

    private fun migrationOf(vararg accounts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (acc in accounts) out.write(fieldBytes(1, acc))
        return out.toByteArray()
    }

    @Test
    fun decodesParameters_includingAlgorithmDigitsTypeCounter() {
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val payload = migrationOf(
            otpParameters(secret, "user@example.com", "Example", algorithm = 2, digits = 2, type = 2),
            otpParameters(secret, "backup", "Example", type = 1, counter = 41)
        )

        val accounts = GoogleAuthDecoder.decodeMigration(payload)
        assertEquals(2, accounts.size)

        assertEquals("Example", accounts[0]["issuer"])
        assertEquals("user@example.com", accounts[0]["name"])
        assertEquals("SHA256", accounts[0]["algorithm"])
        assertEquals("8", accounts[0]["digits"])
        assertEquals("TOTP", accounts[0]["type"])

        assertEquals("HOTP", accounts[1]["type"])
        assertEquals("41", accounts[1]["counter"])
        assertEquals("SHA1", accounts[1]["algorithm"]) // default
        assertEquals("6", accounts[1]["digits"]) // default
    }

    @Test
    fun secretRoundTripsThroughBase32() {
        val secret = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        val payload = migrationOf(otpParameters(secret, "a", "b"))
        val accounts = GoogleAuthDecoder.decodeMigration(payload)
        assertEquals(1, accounts.size)
        val roundTripped = TotpUtils.decodeBase32(accounts[0]["secret"]!!)
        assertTrue(secret.contentEquals(roundTripped))
    }

    @Test
    fun oversizedLengthPrefix_throwsInsteadOfAllocating() {
        // Field 1 with a declared length of ~2 billion but no payload bytes:
        // the old decoder would try ByteArray(2_000_000_000) -> OOM.
        val out = ByteArrayOutputStream()
        out.write(varint((1 shl 3 or 2).toLong()))
        out.write(varint(2_000_000_000L))
        try {
            GoogleAuthDecoder.decodeMigration(out.toByteArray())
            fail("expected EOFException for truncated/oversized payload")
        } catch (expected: EOFException) {
            // ok: rejected cleanly
        }
    }

    @Test
    fun emptyPayload_yieldsNoAccounts() {
        assertTrue(GoogleAuthDecoder.decodeMigration(ByteArray(0)).isEmpty())
    }
}
