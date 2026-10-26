package com.zsecure.com.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun deriveKey_isDeterministic_32Bytes() {
        val salt = CryptoUtils.generateSalt()
        val a = CryptoUtils.deriveKey("correct horse battery staple".toCharArray(), salt)
        val b = CryptoUtils.deriveKey("correct horse battery staple".toCharArray(), salt)
        assertEquals(32, a.size)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun deriveKey_dependsOnSaltAndPassword() {
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }
        val base = CryptoUtils.deriveKey("password123".toCharArray(), salt1)
        val otherSalt = CryptoUtils.deriveKey("password123".toCharArray(), salt2)
        val otherPassword = CryptoUtils.deriveKey("password124".toCharArray(), salt1)
        assertFalse(base.contentEquals(otherSalt))
        assertFalse(base.contentEquals(otherPassword))
    }

    @Test
    fun deriveKey_iterationCountChangesOutput() {
        // The PBKDF2 cost migration relies on this: same password + salt with
        // a different iteration count must produce a different key, and the
        // stored-count mechanism distinguishes legacy vaults from current ones.
        val salt = ByteArray(16) { 7 }
        val legacy = CryptoUtils.deriveKey("password123".toCharArray(), salt, CryptoUtils.LEGACY_ITERATIONS)
        val current = CryptoUtils.deriveKey("password123".toCharArray(), salt, CryptoUtils.ITERATIONS)
        assertFalse(legacy.contentEquals(current))
        assertTrue(CryptoUtils.ITERATIONS > CryptoUtils.LEGACY_ITERATIONS)
    }

    @Test
    fun generateSalt_is16BytesAndRandom() {
        val a = CryptoUtils.generateSalt()
        val b = CryptoUtils.generateSalt()
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }
}
