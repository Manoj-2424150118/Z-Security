package com.zsecure.com.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "two_factor_accounts")
data class TwoFactorAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val issuer: String,
    val name: String,
    val secret: String // Base32 encoded secret key
)
