package com.zsecure.com.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Credential::class, TwoFactorAccount::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun twoFactorDao(): TwoFactorDao

    companion object {
        /**
         * v1 -> v2: per-account OTP parameters (type/algorithm/digits/period/counter)
         * plus a unique index that makes QR re-imports idempotent. Existing rows
         * keep the historical defaults (SHA1, 6 digits, 30 s TOTP). Duplicate rows
         * are de-duplicated first so the unique index can be created safely.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE two_factor_accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'TOTP'")
                db.execSQL("ALTER TABLE two_factor_accounts ADD COLUMN algorithm TEXT NOT NULL DEFAULT 'SHA1'")
                db.execSQL("ALTER TABLE two_factor_accounts ADD COLUMN digits INTEGER NOT NULL DEFAULT 6")
                db.execSQL("ALTER TABLE two_factor_accounts ADD COLUMN periodSec INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE two_factor_accounts ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "DELETE FROM two_factor_accounts WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM two_factor_accounts GROUP BY issuer, name, secret)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_two_factor_accounts_issuer_name_secret " +
                        "ON two_factor_accounts (issuer, name, secret)"
                )
            }
        }
    }
}

object DatabaseProvider {
    private var instance: AppDatabase? = null
    var isLaunchingIntent = false

    /**
     * LEGACY migration key only. Older builds encrypted the vault with this
     * published constant when the user "disabled protection". It is retained
     * solely so those existing vaults can be opened once and re-keyed to a
     * real key (see initialize()). It must NEVER be used to encrypt new data:
     * anyone with the source could open such a vault.
     */
    val LEGACY_DEFAULT_KEY = byteArrayOf(
        0x5A, 0x2B, 0x53, 0x65, 0x63, 0x75, 0x72, 0x65, // Z+Secure
        0x44, 0x65, 0x66, 0x61, 0x75, 0x6C, 0x74, 0x4B, // DefaultK
        0x65, 0x79, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, // ey123456
        0x37, 0x38, 0x39, 0x30, 0x41, 0x42, 0x43, 0x44  // 7890ABCD
    )

    fun rekey(context: Context, newKey: ByteArray) {
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
        require(key.size == 32) { "Vault key must be 32 bytes, got ${key.size}" }
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
                    .addMigrations(AppDatabase.MIGRATION_1_2)
                    .build()
                    
                    // Force database open to verify key is correct
                    instance!!.openHelper.writableDatabase
                } catch (e: Exception) {
                    instance?.close()
                    instance = null

                    // Fallback Plan: Check if database was encrypted using legacy LEGACY_DEFAULT_KEY
                    val legacyRawKey = toRawKey(LEGACY_DEFAULT_KEY)
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
                        .addMigrations(AppDatabase.MIGRATION_1_2)
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
                            .addMigrations(AppDatabase.MIGRATION_1_2)
                            .build()
                            instance!!.openHelper.writableDatabase
                            return@synchronized instance!!
                        } catch (rekeyEx: Exception) {
                            legacyDb.close()
                        }
                    }
                    
                    // NEVER delete the vault file here. The previous behaviour deleted
                    // secure_vault.db on ANY open failure (wrong key, SQLCipher load
                    // error, transient corruption) and silently created an empty vault,
                    // destroying the user's credentials without any warning. Surface the
                    // failure instead; deleting a vault is a user-confirmed action only.
                    throw IllegalStateException(
                        "Unable to open the vault with the provided key. " +
                            "The vault file has been left untouched.",
                        e
                    )
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
