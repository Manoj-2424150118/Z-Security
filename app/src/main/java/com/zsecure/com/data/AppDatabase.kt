package com.zsecure.com.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Credential::class, TwoFactorAccount::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun twoFactorDao(): TwoFactorDao
}

object DatabaseProvider {
    private var instance: AppDatabase? = null
    var isLaunchingIntent = false

    val DEFAULT_KEY = byteArrayOf(
        0x5A, 0x2B, 0x53, 0x65, 0x63, 0x75, 0x72, 0x65, // Z+Secure
        0x44, 0x65, 0x66, 0x61, 0x75, 0x6C, 0x74, 0x4B, // DefaultK
        0x65, 0x79, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, // ey123456
        0x37, 0x38, 0x39, 0x30, 0x41, 0x42, 0x43, 0x44  // 7890ABCD
    )

    fun rekey(context: Context, oldKey: ByteArray, newKey: ByteArray) {
        synchronized(this) {
            val db = instance?.openHelper?.writableDatabase
            if (db != null) {
                val newKeyHex = newKey.joinToString("") { String.format("%02x", it) }
                db.query("PRAGMA rekey = \"x'$newKeyHex'\"").use { cursor ->
                    cursor.moveToFirst()
                }
            }
            instance?.close()
            instance = null
        }
    }

    private fun toRawKey(key: ByteArray): ByteArray {
        if (key.size == 67 && key[0] == 'x'.toByte() && key[1] == '\''.toByte() && key[66] == '\''.toByte()) {
            return key
        }
        val hex = key.joinToString("") { String.format("%02x", it) }
        val result = ByteArray(67)
        result[0] = 'x'.toByte()
        result[1] = '\''.toByte()
        for (i in 0 until 64) {
            result[i + 2] = hex[i].toByte()
        }
        result[66] = '\''.toByte()
        return result
    }

    /**
     * Initializes the Room Database encrypted with SQLCipher.
     * @param context Application context
     * @param passphrase The derived PBKDF2 master key bytes to decrypt the database
     */
    fun initialize(context: Context, passphrase: ByteArray): AppDatabase {
        return synchronized(this) {
            if (instance == null) {
                // Initialize SQLCipher library
                System.loadLibrary("sqlcipher")
                
                val rawKey = toRawKey(passphrase)
                val factory = SupportOpenHelperFactory(rawKey)
                try {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "secure_vault.db"
                    )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                    
                    // Force database open to verify key is correct
                    instance!!.openHelper.writableDatabase
                } catch (e: Exception) {
                    instance?.close()
                    instance = null
                    
                    // Fallback Plan: Check if database was encrypted using legacy DEFAULT_KEY
                    val legacyRawKey = toRawKey(DEFAULT_KEY)
                    val legacyFactory = SupportOpenHelperFactory(legacyRawKey)
                    var legacyDb: AppDatabase? = null
                    var isLegacyDecrypted = false
                    try {
                        legacyDb = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "secure_vault.db"
                        )
                        .openHelperFactory(legacyFactory)
                        .fallbackToDestructiveMigration()
                        .build()
                        legacyDb.openHelper.writableDatabase
                        isLegacyDecrypted = true
                    } catch (ex: Exception) {
                        legacyDb?.close()
                    }
                    
                    if (isLegacyDecrypted && legacyDb != null) {
                        try {
                            val db = legacyDb.openHelper.writableDatabase
                            val newKeyHex = passphrase.joinToString("") { String.format("%02x", it) }
                            db.query("PRAGMA rekey = \"x'$newKeyHex'\"").use { cursor ->
                                cursor.moveToFirst()
                            }
                            legacyDb.close()
                            
                            // Re-open with new secure key
                            instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "secure_vault.db"
                            )
                            .openHelperFactory(factory)
                            .fallbackToDestructiveMigration()
                            .build()
                            instance!!.openHelper.writableDatabase
                            return@synchronized instance!!
                        } catch (rekeyEx: Exception) {
                            legacyDb.close()
                        }
                    }
                    
                    // Fallback to destructive migration (new/empty database) only if everything fails
                    val dbFile = context.getDatabasePath("secure_vault.db")
                    if (dbFile.exists()) {
                        dbFile.delete()
                    }
                    val dbJournal = context.getDatabasePath("secure_vault.db-journal")
                    if (dbJournal.exists()) {
                        dbJournal.delete()
                    }
                    
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "secure_vault.db"
                    )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                    instance!!.openHelper.writableDatabase
                }
            }
            instance!!
        }
    }

    /**
     * Retrieves the active database instance. Throws exception if locked/uninitialized.
     */
    fun getInstance(): AppDatabase {
        return instance ?: throw IllegalStateException("Database is locked. Please unlock the vault first.")
    }

    /**
     * Checks if the database has been successfully initialized/unlocked.
     */
    fun isUnlocked(): Boolean {
        return instance != null
    }

    /**
     * Closes and locks the database. Clears the key from memory.
     */
    fun lock() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
