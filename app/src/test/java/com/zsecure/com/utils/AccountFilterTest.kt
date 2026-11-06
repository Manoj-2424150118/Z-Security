package com.zsecure.com.utils

import com.zsecure.com.data.TwoFactorAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFilterTest {

    private val accounts = listOf(
        TwoFactorAccount(id = 1, issuer = "GitHub", name = "manoj@example.com", secret = "A"),
        TwoFactorAccount(id = 2, issuer = "Google", name = "manoj@gmail.com", secret = "B"),
        TwoFactorAccount(id = 3, issuer = "AWS", name = "root-admin", secret = "C"),
        TwoFactorAccount(id = 4, issuer = "GitLab", name = "manoj-work", secret = "D")
    )

    @Test
    fun blankQuery_returnsListUnchanged() {
        assertSame(accounts, filterTwoFactorAccounts(accounts, ""))
        assertSame(accounts, filterTwoFactorAccounts(accounts, "   "))
    }

    @Test
    fun matchesIssuer_caseInsensitive() {
        val hits = filterTwoFactorAccounts(accounts, "git")
        assertEquals(listOf("GitHub", "GitLab"), hits.map { it.issuer })
    }

    @Test
    fun matchesAccountName() {
        val hits = filterTwoFactorAccounts(accounts, "root-admin")
        assertEquals(1, hits.size)
        assertEquals("AWS", hits[0].issuer)
    }

    @Test
    fun trimsQueryBeforeMatching() {
        val hits = filterTwoFactorAccounts(accounts, "  google  ")
        assertEquals(1, hits.size)
        assertEquals("Google", hits[0].issuer)
    }

    @Test
    fun noMatch_returnsEmpty() {
        assertTrue(filterTwoFactorAccounts(accounts, "dropbox").isEmpty())
    }
}
