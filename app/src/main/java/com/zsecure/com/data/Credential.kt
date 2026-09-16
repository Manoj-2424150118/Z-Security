package com.zsecure.com.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val website: String,
    val username: String,
    val password: String,
    val notes: String
)
