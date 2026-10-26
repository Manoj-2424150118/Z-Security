package com.zsecure.com.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * RFC 6238 Appendix B test vectors (time steps in seconds, T in hex in the RFC).
 * Seeds are the RFC's ASCII seeds, Base32-encoded.
 */
class TotpUtilsTest {

    // ASCII "12345678901234567890" (SHA1 seed, 20 bytes)
    private val sha1Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    // ASCII "12345678901234567890123456789012" (SHA256 seed, 32 bytes)
    private val sha256Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"

    @Test
    fun rfc6238_sha1_8digit() {
        assertEquals("94287082", TotpUtils.generateTotp(sha1Secret, 59_000L, digits = 8))
        assertEquals("07081804", TotpUtils.generateTotp(sha1Secret, 1_111_111_109_000L, digits = 8))
        assertEquals("89005924", TotpUtils.generateTotp(sha1Secret, 1_234_567_890_000L, digits = 8))
    }

    @Test
    fun rfc6238_sha1_6digit_default() {
        assertEquals("287082", TotpUtils.generateTotp(sha1Secret, 59_000L))
        assertEquals("081804", TotpUtils.generateTotp(sha1Secret, 1_111_111_109_000L))
        assertEquals("005924", TotpUtils.generateTotp(sha1Secret, 1_234_567_890_000L))
    }

    @Test
    fun rfc6238_sha256_8digit() {
        assertEquals("46119246", TotpUtils.generateTotp(sha256Secret, 59_000L, digits = 8, algorithm = "SHA256"))
        assertEquals("68084774", TotpUtils.generateTotp(sha256Secret, 1_111_111_109_000L, digits = 8, algorithm = "SHA256"))
        assertEquals("67062674", TotpUtils.generateTotp(sha256Secret, 1_111_111_111_000L, digits = 8, algorithm = "SHA256"))
        assertEquals("91819424", TotpUtils.generateTotp(sha256Secret, 1_234_567_890_000L, digits = 8, algorithm = "SHA256"))
    }

    @Test
    fun period_is_honoured() {
        // With a 60 s step, T=59s is still counter 0; the SHA1 counter-0 code
        // differs from the 30 s-step counter-1 code at the same instant.
        val code60 = TotpUtils.generateTotp(sha1Secret, 59_000L, periodSec = 60)
        val code30 = TotpUtils.generateTotp(sha1Secret, 59_000L, periodSec = 30)
        assertTrue(code60 != null && code30 != null && code60 != code30)
    }

    @Test
    fun hotp_counterCodes() {
        // HOTP counter 1 with the SHA1 seed equals TOTP at T=59s with a 30s step.
        assertEquals("287082", TotpUtils.generateHotp(sha1Secret, 1L))
        // Counter 0 is the RFC 4226 first value for this seed: 755224.
        assertEquals("755224", TotpUtils.generateHotp(sha1Secret, 0L))
    }

    @Test
    fun invalidSecret_returnsNull_notFakeCode() {
        assertNull(TotpUtils.generateTotp("not!base32", 59_000L))
        assertNull(TotpUtils.generateTotp("", 59_000L))
        assertNull(TotpUtils.generateHotp("!!!", 0L))
    }

    @Test
    fun decodeBase32_rfc4648_vectors() {
        assertEquals("", String(TotpUtils.decodeBase32("")))
        assertEquals("f", String(TotpUtils.decodeBase32("MY======")))
        assertEquals("fo", String(TotpUtils.decodeBase32("MZXQ====")))
        assertEquals("foo", String(TotpUtils.decodeBase32("MZXW6===")))
        assertEquals("foob", String(TotpUtils.decodeBase32("MZXW6YQ=")))
        assertEquals("fooba", String(TotpUtils.decodeBase32("MZXW6YTB")))
        assertEquals("foobar", String(TotpUtils.decodeBase32("MZXW6YTBOI======")))
    }

    @Test
    fun decodeBase32_toleratesLowercaseSpacesHyphens() {
        assertEquals("foo", String(TotpUtils.decodeBase32("mzxw6===")))
        assertEquals("foo", String(TotpUtils.decodeBase32("MZ XW6")))
        assertEquals("foo", String(TotpUtils.decodeBase32("MZ-XW6")))
    }

    @Test
    fun decodeBase32_invalidCharThrows() {
        try {
            TotpUtils.decodeBase32("ABC1") // '1' is not a Base32 character
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun formatCode_groupsEvenLengths() {
        assertEquals("123 456", TotpUtils.formatCode("123456"))
        assertEquals("1234 5678", TotpUtils.formatCode("12345678"))
        assertEquals("1234567", TotpUtils.formatCode("1234567"))
    }
}
