package com.zsecure.com.utils

import com.zsecure.com.data.TwoFactorAccount

/**
 * Pure filter behind the Authenticator search box: an account matches
 * when the trimmed query appears in its issuer or its account name
 * (case-insensitive). A blank query returns the list unchanged.
 *
 * The vault (ManagerScreen) has had search since the beginning; the
 * Authenticator list — the screen users open most, often with dozens
 * of issuers — had none. Kept free of Compose/Android types so it is
 * unit-testable on the JVM.
 */
fun filterTwoFactorAccounts(
    accounts: List<TwoFactorAccount>,
    query: String
): List<TwoFactorAccount> {
    val needle = query.trim()
    if (needle.isEmpty()) return accounts
    return accounts.filter {
        it.issuer.contains(needle, ignoreCase = true) ||
            it.name.contains(needle, ignoreCase = true)
    }
}
