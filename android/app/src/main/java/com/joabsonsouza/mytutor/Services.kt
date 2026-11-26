package com.joabsonsouza.mytutor

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Preferências da conversa (igual ao @AppStorage do iOS). */
object Settings {
    var listenByDefault: Boolean
        get() = AppData.prefs.getBoolean("listenByDefault", false)
        set(value) = AppData.prefs.edit().putBoolean("listenByDefault", value).apply()

    var maleVoice: Boolean
        get() = AppData.prefs.getBoolean("maleVoice", false)
        set(value) = AppData.prefs.edit().putBoolean("maleVoice", value).apply()
}

/**
 * Tradução inglês → português no próprio aparelho (ML Kit, equivalente ao Translation da Apple).
 * Na primeira vez baixa o idioma (~30 MB); depois funciona sem internet.
 */
object Translator {
    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                .build()
        )
    }

    suspend fun toPortuguese(text: String): String {
        suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
