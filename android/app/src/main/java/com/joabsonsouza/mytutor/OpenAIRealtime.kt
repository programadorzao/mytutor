package com.joabsonsouza.mytutor

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Conversa por voz com a OpenAI Realtime, com o mesmo comportamento do app iOS (ios/EnglishTutor/Realtime.swift):
 * segure para falar, ela responde com voz e o texto das duas falas aparece na tela.
 *
 * No iOS a conexão é por WebRTC; aqui é por WebSocket (mesma API, mesmos eventos), que reaproveita o
 * microfone e o alto-falante do Android sem precisar de uma biblioteca de WebRTC.
 * A chave é a da própria pessoa e vai direto do celular para a OpenAI.
 */
class OpenAIRealtime : VoiceSession {
    override var status by mutableStateOf(VoiceStatus.Idle)
        private set
    override var error by mutableStateOf<String?>(null)
    override val lines = mutableStateListOf<Line>()
    override var audioLevel by mutableStateOf(0f)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null
    private var session = JSONObject()
    private var ready = false
    private var responseActive = false

    // Áudio
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var track: AudioTrack? = null
    private var micThread: Thread? = null
    private var playThread: Thread? = null
    private val playQueue = LinkedBlockingQueue<ByteArray>()
    private var speakingUntil = 0L

    /** Estado do botão de falar, usado também pela thread do microfone. */
    private val mic = Any()
    private var micOn = false
    /** Maior volume do seu microfone enquanto o botão está pressionado. */
    private var micPeak = 0.0

    override fun start(apiKey: String, instructions: String, voice: String, history: List<Line>) {
        error = null
        lines.clear()
        lines.addAll(history)
        if (apiKey.isBlank()) {
            error = "Falta a chave da OpenAI."
            return
        }
        status = VoiceStatus.Connecting
        ready = false
        responseActive = false
        session = JSONObject()
            .put("type", "realtime")
            .put("model", MODEL)
            .put("output_modalities", JSONArray().put("audio"))
            .put("instructions", instructions)
            .put(
                "audio", JSONObject()
                    .put(
                        "input", JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                            .put(
                                "transcription", JSONObject()
                                    .put("model", "gpt-4o-mini-transcribe")
                                    // O texto na tela deve mostrar o que você falou de verdade, com os erros.
                                    .put("prompt", "Transcribe exactly what the speaker says, word for word, including grammar mistakes, wrong words, repetitions and filler words. Do not correct or improve anything. The speaker is a Brazilian learning English and may mix in Portuguese.")
                            )
                            .put("turn_detection", JSONObject.NULL) // aperte para falar
                    )
                    .put(
                        "output", JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                            .put("voice", voice)
                    )
            )
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=$MODEL")
            .header("Authorization", "Bearer $apiKey")
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    // region Segure para falar

    override fun pressToTalk() {
        send("""{"type":"input_audio_buffer.clear"}""")
        if (responseActive) send("""{"type":"response.cancel"}""")
        if (status == VoiceStatus.Speaking) stopPlayback()
        synchronized(mic) {
            micPeak = 0.0
            micOn = true
        }
    }

    override fun releaseToTalk() {
        // Espera um pouco para não cortar a última sílaba.
        main.postDelayed({
            val peak = synchronized(mic) {
                micOn = false
                micPeak
            }
            if (status == VoiceStatus.Idle) return@postDelayed
            // Soltou sem falar: descarta o áudio (silêncio/ruído) em vez de mandar ela responder.
            if (peak <= VOICE_THRESHOLD) {
                send("""{"type":"input_audio_buffer.clear"}""")
            } else {
                send("""{"type":"input_audio_buffer.commit"}""")
                send("""{"type":"response.create"}""")
            }
        }, 300)
    }

    // endregion

    override fun stop() {
        synchronized(mic) { micOn = false }
        socket?.close(1000, null)
        socket = null
        ready = false
        responseActive = false
        stopAudio()
        audioLevel = 0f
        status = VoiceStatus.Idle
    }

    private fun fail(message: String) {
        stop()
        error = friendly(message)
    }

    private fun send(text: String) {
        socket?.send(text)
    }

    // region WebSocket

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(JSONObject().put("type", "session.update").put("session", session).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            main.post { if (webSocket === socket) handle(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            main.post { if (webSocket === socket && status != VoiceStatus.Idle) fail(reason.ifBlank { "connection lost" }) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Chave errada ou sem crédito: a OpenAI responde antes de abrir o WebSocket.
            val reason = when (response?.code) {
                401 -> "invalid api key"
                429 -> "quota"
                else -> t.message ?: ""
            }
            main.post { if (webSocket === socket && status != VoiceStatus.Idle) fail(reason) }
        }
    }

    private fun handle(text: String) {
        if (status == VoiceStatus.Idle) return
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "session.updated" -> if (!ready) {
                ready = true
                try {
                    startAudio()
                } catch (e: Exception) {
                    fail("Erro no áudio: ${e.message}")
                    return
                }
                status = VoiceStatus.Listening
                // Ela começa falando.
                send("""{"type":"response.create"}""")
            }
            "response.created" -> responseActive = true
            "response.done" -> responseActive = false
            "response.output_audio.delta" -> json.optString("delta").takeIf { it.isNotEmpty() }?.let {
                play(Base64.decode(it, Base64.DEFAULT))
            }
            "conversation.item.input_audio_transcription.completed" ->
                json.optString("transcript").trim().takeIf { it.isNotEmpty() }?.let { lines.add(Line(Line.Role.Me, it)) }
            "response.output_audio_transcript.done" ->
                json.optString("transcript").trim().takeIf { it.isNotEmpty() }?.let { lines.add(Line(Line.Role.Tutor, it)) }
            "error" -> {
                val err = json.optJSONObject("error")
                val code = err?.optString("code").orEmpty()
                // Toque rápido demais no botão, ou nada para cancelar: não é erro de verdade.
                if (code == "input_audio_buffer_commit_empty" || code == "response_cancel_not_active") return
                val message = err?.optString("message").orEmpty().ifBlank { "Erro desconhecido" }
                if (ready) error = friendly(message) else fail(message)
            }
        }
    }

    // endregion

    // region Áudio

    @SuppressLint("MissingPermission") // a tela pede o microfone antes de começar
    private fun startAudio() {
        // Microfone: 24 kHz (o formato da OpenAI), com cancelamento de eco do próprio Android.
        val minIn = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 9600)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("microfone indisponível")
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        }
        recorder = rec
        rec.startRecording()
        micThread = Thread({ micLoop(rec) }, "mic").apply { start() }

        // Voz dela: 24 kHz, pelo alto-falante.
        val minOut = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minOut * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        playQueue.clear()
        playThread = Thread({ playLoop(t) }, "voice").apply { start() }
    }

    /** Lê o microfone em pedaços de 40 ms e envia enquanto o botão está apertado. */
    private fun micLoop(rec: AudioRecord) {
        val chunk = ShortArray(960)
        val bytes = ByteArray(chunk.size * 2)
        while (!Thread.currentThread().isInterrupted) {
            val n = rec.read(chunk, 0, chunk.size)
            if (n <= 0) {
                if (n < 0) break
                continue
            }
            if (!synchronized(mic) { micOn }) continue
            var sum = 0.0
            for (i in 0 until n) {
                val s = chunk[i]
                bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
                val f = s / 32768.0
                sum += f * f
            }
            val level = sqrt(sum / n)
            synchronized(mic) { micPeak = maxOf(micPeak, level) }
            val data = Base64.encodeToString(bytes, 0, n * 2, Base64.NO_WRAP)
            socket?.send("""{"type":"input_audio_buffer.append","audio":"$data"}""")
        }
    }

    /** Toca os pedaços da voz dela em fila e mede o volume para a boca. */
    private fun playLoop(t: AudioTrack) {
        while (!Thread.currentThread().isInterrupted) {
            val pcm = try {
                playQueue.take()
            } catch (_: InterruptedException) {
                break
            }
            var sum = 0.0
            val count = pcm.size / 2
            for (i in 0 until count) {
                val s = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort() / 32768.0
                sum += s * s
            }
            val level = if (count > 0) sqrt(sum / count).toFloat() else 0f
            main.post { audioLevel = level }
            t.write(pcm, 0, pcm.size)
        }
    }

    private fun play(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        playQueue.offer(pcm)
        status = VoiceStatus.Speaking
        // A fala termina depois do último pedaço, pela duração de cada um.
        val now = SystemClock.elapsedRealtime()
        speakingUntil = maxOf(now, speakingUntil) + pcm.size / 2 * 1000L / RATE
        val until = speakingUntil
        main.postDelayed({
            if (status == VoiceStatus.Speaking && SystemClock.elapsedRealtime() >= speakingUntil && until == speakingUntil) {
                status = VoiceStatus.Listening
                audioLevel = 0f
            }
        }, until - now + 150)
    }

    /** Você apertou para falar: corta a voz dela na hora. */
    private fun stopPlayback() {
        playQueue.clear()
        track?.let {
            it.pause()
            it.flush()
            it.play()
        }
        speakingUntil = 0
        audioLevel = 0f
        if (status == VoiceStatus.Speaking) status = VoiceStatus.Listening
    }

    private fun stopAudio() {
        micThread?.interrupt()
        micThread = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        echoCanceler?.release()
        echoCanceler = null
        playThread?.interrupt()
        playThread = null
        playQueue.clear()
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        speakingUntil = 0
    }

    // endregion

    companion object {
        private const val MODEL = "gpt-realtime-2.1"
        private const val RATE = 24000
        /** Volume a partir do qual consideramos que você falou (igual ao iOS). */
        private const val VOICE_THRESHOLD = 0.02

        /** Mensagens da OpenAI (em inglês e técnicas) em português claro. */
        fun friendly(message: String): String {
            val m = message.lowercase()
            return when {
                "quota" in m || "insufficient" in m || "billing" in m || "rate limit" in m ->
                    "A OpenAI recusou por falta de crédito ou limite. Confira o saldo da sua conta."
                "api key" in m || "api_key" in m || "unauthorized" in m || "authentication" in m ->
                    "A OpenAI recusou sua chave. Confira a chave."
                "microfone" in m || "áudio" in m -> message
                "context" in m || "too long" in m || "exceed" in m || "expired" in m || "maximum duration" in m ->
                    "A conversa ficou longa demais e a OpenAI encerrou."
                else -> "A conexão com a OpenAI caiu. Tente de novo."
            }
        }
    }
}
