package com.zsecure.com.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored 2FA account.
 *
 * The [algorithm], [digits] and [periodSec] columns were added in DB v2:
 * previously every account was assumed to be SHA1 / 6 digits / 30 s TOTP,
 * which silently produced wrong codes for issuers using other parameters.
 * [type] is "TOTP" or "HOTP"; HOTP accounts use [counter].
 */
@Entity(
    tableName = "two_factor_accounts",
    indices = [Index(value = ["issuer", "name", "secret"], unique = true)]
)
data class TwoFactorAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val issuer: String,
    val name: String,
    val secret: String, // Base32 encoded secret key
    // defaultValue must match MIGRATION_1_2's ALTER TABLE defaults so Room's
    // post-migration schema validation passes.
    @ColumnInfo(defaultValue = "TOTP") val type: String = "TOTP", // TOTP | HOTP
    @ColumnInfo(defaultValue = "SHA1") val algorithm: String = "SHA1", // SHA1 | SHA256 | SHA512 | MD5
    @ColumnInfo(defaultValue = "6") val digits: Int = 6,
    @ColumnInfo(defaultValue = "30") val periodSec: Int = 30,
    @ColumnInfo(defaultValue = "0") val counter: Long = 0
)
