package com.example.smartlockmobile.security

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val algorithm = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val iterations = if (Build.VERSION.SDK_INT >= 26) 120_000 else 20_000
        val derived = derive(secret, salt, algorithm, iterations)
        return listOf(algorithm, iterations.toString(), encode(salt), encode(derived)).joinToString("$")
    }

    fun verify(secret: String, stored: String?): Boolean {
        if (stored == null) return false
        val parts = stored.split("$")
        if (parts.size != 4) return false
        return try {
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = derive(secret, salt, parts[0], iterations)
            MessageDigest.isEqual(expected, actual) // comparación en tiempo constante
        } catch (e: Exception) {
            false
        }
    }

    private fun derive(secret: String, salt: ByteArray, algorithm: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}

object InputValidator {
    private val USER_REGEX = Regex("^[A-Za-z0-9_.-]{3,20}$")
    private val PIN_REGEX = Regex("^[0-9]{4,8}$")

    fun usernameError(value: String): String? =
        if (USER_REGEX.matches(value)) null
        else "El usuario debe tener 3 a 20 caracteres (letras, números, . _ -)."

    fun passwordError(value: String): String? = when {
        value.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
        value.none { it.isLetter() } || value.none { it.isDigit() } ->
            "La contraseña debe incluir letras y números."
        else -> null
    }

    fun isValidPin(value: String): Boolean = PIN_REGEX.matches(value)
}
