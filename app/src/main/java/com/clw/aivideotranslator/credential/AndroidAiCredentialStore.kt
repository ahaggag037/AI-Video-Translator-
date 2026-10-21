package com.clw.aivideotranslator.credential

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class StoredCredential(
    val apiKey: String,
    val status: CredentialStatus,
)

/**
 * App-private encrypted credential store. Ciphertext lives in SharedPreferences while the AES key
 * is non-exportable AndroidKeyStore material. No validation request is made when saving.
 */
internal class AndroidAiCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): StoredCredential? {
        val encodedCiphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val encodedIv = prefs.getString(KEY_IV, null) ?: throw SecurityException("credential IV missing")
        val status = prefs.getString(KEY_STATUS, null)
            ?.let { runCatching { CredentialStatus.valueOf(it) }.getOrNull() }
            ?: CredentialStatus.UNVERIFIED

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
        )
        val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            .toString(Charsets.UTF_8)
        require(plaintext.isNotBlank()) { "stored provider credential is blank" }
        return StoredCredential(apiKey = plaintext, status = status)
    }

    fun save(apiKey: String): StoredCredential {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "provider credential is blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val saved = prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_STATUS, CredentialLifecycle.onSecretSaved().name)
            .commit()
        check(saved) { "provider credential persistence failed" }
        return StoredCredential(normalized, CredentialStatus.UNVERIFIED)
    }

    fun markUsable() = updateStatus(CredentialLifecycle.onProviderSuccess())

    fun markRejected() = updateStatus(CredentialLifecycle.onDefiniteAuthRejection())

    fun remove() {
        val removed = prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_STATUS)
            .commit()
        check(removed) { "provider credential removal failed" }
    }

    private fun updateStatus(status: CredentialStatus) {
        check(prefs.contains(KEY_CIPHERTEXT)) { "cannot update missing provider credential" }
        check(prefs.edit().putString(KEY_STATUS, status.name).commit()) {
            "provider credential status persistence failed"
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "provider_credential_v1"
        const val KEY_ALIAS = "ai_video_translator_provider_key_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_STATUS = "status"
    }
}
