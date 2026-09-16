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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: TwoFactorAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<TwoFactorAccount>)

    @Delete
    suspend fun delete(account: TwoFactorAccount)
}
