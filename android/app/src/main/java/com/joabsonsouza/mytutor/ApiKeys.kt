package com.joabsonsouza.mytutor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Qual IA conversa com você (por enquanto só o Gemini; a OpenAI entra depois). */
enum class Provider { Gemini, OpenAI }

/**
 * Chaves das IAs que a própria pessoa cadastra no app (igual ao iOS, ios/EnglishTutor/APIKeys.swift).
 * Ficam só neste aparelho, criptografadas com uma chave do Android Keystore que não sai do celular,
 * e só são enviadas direto para o Google ou a OpenAI.
 */
class ApiKeys(context: Context) {
    private val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)

    var gemini by mutableStateOf(read("gemini"))
        private set
    var openAI by mutableStateOf(read("openai"))
        private set

    fun key(provider: Provider) = if (provider == Provider.OpenAI) openAI else gemini

    fun save(value: String, provider: Provider) {
        val clean = value.trim()
        val name = if (provider == Provider.OpenAI) "openai" else "gemini"
        if (clean.isEmpty()) prefs.edit().remove(name).apply() else prefs.edit().putString(name, encrypt(clean)).apply()
        if (provider == Provider.OpenAI) openAI = clean else gemini = clean
    }

    private fun read(name: String): String =
        prefs.getString(name, null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: ""

    // region Criptografia (Android Keystore, AES-GCM)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val data = cipher.iv + cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12))
    }

    // endregion

    companion object {
        private const val ALIAS = "mytutor_api_keys"
        private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

        /**
         * Testa a chave com uma chamada simples (listar modelos, não gasta nada).
         * Devolve null se funcionou, ou a mensagem do problema. Rode fora da thread principal.
         */
        fun check(key: String, provider: Provider): String? {
            val request = when (provider) {
                Provider.Gemini -> Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models")
                    .header("x-goog-api-key", key)
                Provider.OpenAI -> Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .header("Authorization", "Bearer $key")
            }.build()
            return try {
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> null
                        429 -> "Essa chave atingiu o limite agora. Ela parece válida; tente mais tarde."
                        else -> "Essa chave não funcionou. Confira se copiou ela inteira."
                    }
                }
            } catch (_: Exception) {
                "Não deu para testar a chave. Você está sem internet?"
            }
        }
    }
}
