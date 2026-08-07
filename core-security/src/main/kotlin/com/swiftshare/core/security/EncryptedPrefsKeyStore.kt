package com.swiftshare.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [KeyStore] backed by [EncryptedSharedPreferences] — the file on disk is
 * itself AES-encrypted with a key held in the Android Keystore, so raw
 * session keys never sit in plaintext (NFR-05.2).
 */
class EncryptedPrefsKeyStore(context: Context) : KeyStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun storeSessionKey(peerIdHash: String, key: ByteArray) {
        prefs.edit().putString(keyPrefix + peerIdHash, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }

    override fun sessionKeyFor(peerIdHash: String): ByteArray? =
        prefs.getString(keyPrefix + peerIdHash, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    override fun removeSessionKey(peerIdHash: String) {
        prefs.edit().remove(keyPrefix + peerIdHash).apply()
    }

    internal fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    internal fun getString(key: String): String? = prefs.getString(key, null)

    companion object {
        private const val PREFS_FILE_NAME = "swiftshare_security_prefs"
        private const val keyPrefix = "session_key_"
    }
}
