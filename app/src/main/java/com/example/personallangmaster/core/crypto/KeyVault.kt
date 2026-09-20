package com.example.personallangmaster.core.crypto

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
 * Шифрует секреты (прежде всего ключ Gemini API) ключом из Android Keystore.
 *
 * Сам ключ шифрования не покидает защищённое хранилище устройства и не попадает
 * ни в резервную копию, ни в APK — наружу отдаётся только строка вида
 * base64(IV + шифротекст), которую уже можно спокойно класть в DataStore.
 */
class KeyVault {

    /** Шифрует строку. Возвращает null, если Keystore недоступен. */
    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "Не удалось зашифровать секрет: ${it.message}") }.getOrNull()

    /** Расшифровывает строку, полученную из [encrypt]. */
    fun decrypt(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE) { "слишком короткий шифротекст" }
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val body = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Не удалось расшифровать секрет: ${it.message}") }.getOrNull()
    }

    /**
     * Удаляет ключ шифрования. После этого ранее сохранённые секреты
     * восстановить невозможно — используется при полном сбросе данных.
     */
    fun wipe() = runCatching {
        KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
    }.isSuccess

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "KeyVault"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "personallangmaster.secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 256
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
