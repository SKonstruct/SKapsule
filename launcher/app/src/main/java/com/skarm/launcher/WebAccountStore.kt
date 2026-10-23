package com.skarm.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The saved Spiral Knights web account, for auto login.
 *
 * The pair is encrypted with an AES key that lives in the Android Keystore, so the stored text
 * is useless off this device and out of backups. The password is never written anywhere in
 * plaintext; the launcher passes it to the game process only at launch.
 */
object WebAccountStore {
    private const val TAG = "WebAccountStore"
    private const val PREFS = "web_account"
    private const val KEY_BLOB = "blob"
    private const val KEY_ALIAS = "sk_web_account"

    /** The game's login fields; the native side holds 255 bytes each. */
    const val MAX_LENGTH = 100

    data class Account(val username: String, val password: String)

    fun load(context: Context): Account? {
        val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BLOB, null)
            ?: return null
        return try {
            decode(decrypt(blob))
        } catch (e: Exception) {
            // A restored or reset keystore cannot open the old blob; treat it as no account.
            Log.w(TAG, "Saved web account is unreadable; ignoring it", e)
            null
        }
    }

    /** @return false if the account could not be saved. */
    fun save(context: Context, account: Account): Boolean = try {
        val blob = encrypt(encode(account))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BLOB, blob).apply()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not save the web account", e)
        false
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_BLOB).apply()
    }

    // Newline-separated: neither field can contain one (the dialog is single-line).
    internal fun encode(account: Account): String = account.username + "\n" + account.password

    internal fun decode(text: String): Account? {
        val split = text.indexOf('\n')
        if (split <= 0 || split == text.length - 1) return null
        return Account(text.substring(0, split), text.substring(split + 1))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** IV (12 bytes) followed by the ciphertext, Base64. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }
}
