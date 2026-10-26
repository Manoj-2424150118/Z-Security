package com.zsecure.com.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY website ASC")
    fun getAllCredentials(): Flow<List<Credential>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: Credential)

    @Update
    suspend fun update(credential: Credential)

    @Delete
    suspend fun delete(credential: Credential)
}

@Dao
interface TwoFactorDao {
    @Query("SELECT * FROM two_factor_accounts ORDER BY issuer ASC, name ASC")
    fun getAllAccounts(): Flow<List<TwoFactorAccount>>

    // IGNORE (not REPLACE): imported accounts always arrive with id = 0, so
    // REPLACE could never match an existing row and re-scanning the same QR
    // duplicated every account. The unique (issuer, name, secret) index plus
    // IGNORE makes re-imports idempotent.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: TwoFactorAccount)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(accounts: List<TwoFactorAccount>)

    @Query("UPDATE two_factor_accounts SET counter = :counter WHERE id = :id")
    suspend fun updateCounter(id: Int, counter: Long)

    @Delete
    suspend fun delete(account: TwoFactorAccount)
}
