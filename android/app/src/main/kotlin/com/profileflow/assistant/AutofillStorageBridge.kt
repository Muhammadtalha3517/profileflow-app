package com.profileflow.assistant

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Native bridge to access encrypted profile data stored by Flutter (`flutter_secure_storage`)
 * or Jetpack Security (`EncryptedSharedPreferences`).
 * 
 * Strict Privacy Guidelines:
 * - NEVER log sensitive values (passwords, emails, phone numbers).
 * - All data is kept strictly in isolated hardware-backed keystore memory.
 * - Read-only operation from AutofillService context.
 */
class AutofillStorageBridge(private val context: Context) {

    companion object {
        private const val TAG = "ProfileFlowBridge"
        private const val SECURE_STORAGE_PREFS = "FlutterSecureStorage"
        private const val FALLBACK_ENCRYPTED_PREFS = "profileflow_vault_secure"
        private const val KEY_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg" // flutter_secure_storage default prefix
        private const val SETTING_BIOMETRIC_REQUIRED = "profileflow_biometric_required"
    }

    private fun getEncryptedPreferences(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                FALLBACK_ENCRYPTED_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing hardware-backed EncryptedSharedPreferences: ${e.message}")
            null
        }
    }

    /**
     * Resolves a requested profile field.
     * Tries:
     * 1. flutter_secure_storage default SharedPreferences container
     * 2. EncryptedSharedPreferences direct cache synced via MethodChannel
     */
    fun getProfileField(key: String): String? {
        try {
            // First check FlutterSecureStorage shared preferences
            val flutterPrefs = context.getSharedPreferences(SECURE_STORAGE_PREFS, Context.MODE_PRIVATE)
            val prefixedKey = "${KEY_PREFIX}_$key"
            val flutterValue = flutterPrefs.getString(prefixedKey, null) ?: flutterPrefs.getString(key, null)

            if (!flutterValue.isNullOrBlank()) {
                return flutterValue
            }

            // Fallback to Android Jetpack EncryptedSharedPreferences
            val encPrefs = getEncryptedPreferences()
            val encValue = encPrefs?.getString(key, null)
            if (!encValue.isNullOrBlank()) {
                return encValue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing secure storage for field: $key (content omitted for security)")
        }
        return null
    }

    /**
     * Synchronizes a key-value pair from Flutter MethodChannel into native encrypted cache
     * so that AutofillService can immediately read it without spawning a Flutter engine.
     */
    fun storeProfileField(key: String, value: String?) {
        try {
            val encPrefs = getEncryptedPreferences()
            encPrefs?.edit()?.apply {
                if (value == null) {
                    remove(key)
                } else {
                    putString(key, value)
                }
                apply()
            }
            Log.d(TAG, "Encrypted field synced: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync encrypted field: $key")
        }
    }

    /**
     * Checks if user requires biometric authentication before datasets are populated.
     * Defaults to true for zero-trust security.
     */
    fun isBiometricRequired(): Boolean {
        val encPrefs = getEncryptedPreferences()
        return encPrefs?.getBoolean(SETTING_BIOMETRIC_REQUIRED, true) ?: true
    }

    fun setBiometricRequired(required: Boolean) {
        val encPrefs = getEncryptedPreferences()
        encPrefs?.edit()?.putBoolean(SETTING_BIOMETRIC_REQUIRED, required)?.apply()
    }
}
