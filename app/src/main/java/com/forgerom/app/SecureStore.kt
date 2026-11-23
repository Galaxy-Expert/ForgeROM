package com.forgerom.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (the user's API key) with a key that lives in the Android Keystore.
 * The key cannot be exported from the device, so a copy of the app's files alone is useless.
 */
object SecureStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "forgerom_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns Base64(iv + ciphertext). May throw if the Keystore is unavailable. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /** Returns null if the data cannot be decrypted (for example the Keystore key was lost). */
    fun decrypt(stored: String): String? = try {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, IV_SIZE)
        val encrypted = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
