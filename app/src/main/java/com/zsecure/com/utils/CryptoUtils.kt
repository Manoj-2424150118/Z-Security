package com.zsecure.com.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {

    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256 // in bits
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "zsecure_master_key"

    /**
     * Generates a secure random 16-byte salt.
     */
    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    /**
     * Derives a cryptographic key from a password and salt using PBKDF2.
     * Clears Spec password immediately for memory safety.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = factory.generateSecret(spec)
            secretKey.encoded
        } catch (e: NoSuchAlgorithmException) {
            byteArrayOf()
        } catch (e: InvalidKeySpecException) {
            byteArrayOf()
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Obtains EncryptedSharedPreferences backed by Android Keystore.
     * Falls back to private SharedPreferences if Keystore fails.
     */
    fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                "aegispass_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("aegispass_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrates legacy unencrypted preferences to EncryptedSharedPreferences and wipes old file.
     */
    fun migrateLegacyPreferences(context: Context) {
        try {
            val legacyPrefs = context.getSharedPreferences("aegispass_prefs", Context.MODE_PRIVATE)
            if (legacyPrefs.all.isNotEmpty()) {
                val securePrefs = getEncryptedSharedPreferences(context)
                val editor = securePrefs.edit()
                for ((key, value) in legacyPrefs.all) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                editor.commit()
                legacyPrefs.edit().clear().commit()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences("aegispass_prefs")
                }
            }
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    /**
     * Explicitly overwrites memory buffers with zeros.
     */
    fun clearArray(array: ByteArray) {
        for (i in array.indices) {
            array[i] = 0
        }
    }

    fun clearArray(array: CharArray) {
        for (i in array.indices) {
            array[i] = '\u0000'
        }
    }

    /**
     * Generates a Master Key using KeyGenParameterSpec (AES/GCM/NoPadding, 256-bit)
     * inside the hardware-backed Android Keystore system.
     */
    fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
