package com.example.personallangmaster.core.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Хеширование родительского PIN.
 *
 * PIN защищает настройки ключа и лимитов от ребёнка, а не от взлома устройства,
 * поэтому достаточно соли и SHA-256 — но хранить PIN в открытом виде всё равно нельзя.
 */
object PinHasher {

    private const val SEPARATOR = ":"

    /** Возвращает строку «соль:хеш» для записи в настройки. */
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val digest = digest(pin, salt)
        return encode(salt) + SEPARATOR + encode(digest)
    }

    /** Проверяет PIN против сохранённого значения. */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) return false
        val salt = runCatching { decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { decode(parts[1]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(digest(pin, salt), expected)
    }

    private fun digest(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray(Charsets.UTF_8))
        }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private const val SALT_SIZE = 16
}
