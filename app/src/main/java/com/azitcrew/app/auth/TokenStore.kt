package com.azitcrew.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {
    private const val PREFS_FILE = "azit_secure_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefs = it }
    }

    fun save(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun load(context: Context): String? = getPrefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun clear(context: Context) {
        getPrefs(context).edit().remove(KEY_ACCESS_TOKEN).apply()
    }
}
