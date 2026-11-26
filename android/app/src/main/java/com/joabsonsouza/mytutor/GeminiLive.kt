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
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class VoiceStatus { Idle, Connecting, Listening, Speaking }

data class Line(val role: Role, val text: String, val id: String = UUID.randomUUID().toString()) {
    enum class Role { Me, Tutor }
}

/**
 * Conversa por voz com o Gemini Live (WebSocket), igual ao app iOS (ios/EnglishTutor/GeminiLive.swift):
 * segure para falar, ela responde com voz e o texto das duas falas aparece na tela.
 */
class GeminiLive {
    var status by mutableStateOf(VoiceStatus.Idle)
        private set
    var error by mutableStateOf<String?>(null)
    val lines = mutableStateListOf<Line>()
    /** Volume da voz dela (0 a ~1), para a boca do avatar. */
    var audioLevel by mutableStateOf(0f)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null
    private var setup = JSONObject()
    private var resumeHandle: String? = null
    private var reconnecting = false
    private var openMine: Int? = null
    private var openTutor: Int? = null

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
    private var micStarted = false
    private val preroll = ArrayDeque<String>()

    fun start(apiKey: String, instructions: String, voice: String, history: List<Line>) {
        error = null
        lines.clear()
        lines.addAll(history)
        openMine = null
        openTutor = null
        if (apiKey.isBlank()) {
            error = "Falta a chave do Gemini."
            return
        }
        status = VoiceStatus.Connecting
        setup = JSONObject()
            .put("model", "models/gemini-3.8-live")
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
                        )
                    )
            )
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
            // Sua fala na tela: inglês (com português quando você trava).
            .put("inputAudioTranscription", JSONObject().put("languageCodes", JSONArray().put("en-US").put("pt-BR")))
            .put("outputAudioTranscription", JSONObject())
            // Segure para falar: sem detecção automática de fala.
            .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject().put("disabled", true)))
            // Sem isso a conversa por voz acaba em ~15 min (contexto cheio).
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
        resumeHandle = null
        reconnecting = false
        this.apiKey = apiKey
        connect()
    }

    private var apiKey = ""

    /** Abre a conexão; com resumeHandle, retoma a mesma sessão (contexto preservado). */
    private fun connect() {
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey")
            .build()
        val ws = client.newWebSocket(request, Listener())
        socket = ws
        val s = JSONObject(setup.toString())
        s.put("sessionResumption", resumeHandle?.let { JSONObject().put("handle", it) } ?: JSONObject())
        ws.send(JSONObject().put("setup", s).toString())
    }

    // region Segure para falar

    fun pressToTalk() {
        if (status == VoiceStatus.Speaking) stopPlayback()
        openMine = null // nova fala sua começa um balão novo
        synchronized(mic) {
            micOn = true
            micStarted = false
            preroll.clear()
        }
    }

    fun releaseToTalk() {
        // Espera um pouco para não cortar a última sílaba.
        main.postDelayed({
            val started = synchronized(mic) {
                val s = micStarted
                micOn = false
                micStarted = false
                preroll.clear()
                s
            }
            // Soltou sem falar: nada foi enviado. Sem áudio, o Gemini recusa o activityEnd e fecha.
            if (started) socket?.send("""{"realtimeInput":{"activityEnd":{}}}""")
        }, 300)
    }

    // endregion

    fun stop() {
        synchronized(mic) { micOn = false; micStarted = false; preroll.clear() }
        socket?.close(1000, null)
        socket = null
        resumeHandle = null
        reconnecting = false
        stopAudio()
        audioLevel = 0f
        status = VoiceStatus.Idle
    }

    private fun fail(message: String) {
        stop()
        error = friendly(message)
    }

    // region WebSocket

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            main.post { if (webSocket === socket) handle(text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            main.post { if (webSocket === socket) handle(bytes.utf8()) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            main.post { if (webSocket === socket) dropped(reason) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            main.post { if (webSocket === socket) dropped(t.message ?: "") }
        }
    }

    /** Caiu: retoma a sessão em silêncio se der; senão mostra o erro. */
    private fun dropped(reason: String) {
        if (status == VoiceStatus.Idle) return
        if (resumeHandle != null && !reconnecting) {
            reconnecting = true
            connect()
            return
        }
        fail(reason.ifBlank { "connection lost" })
    }

    private fun handle(text: String) {
        if (status == VoiceStatus.Idle) return
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return

        json.optJSONObject("sessionResumptionUpdate")?.optString("newHandle")?.takeIf { it.isNotEmpty() }?.let {
            resumeHandle = it
        }

        // O Gemini avisa antes de derrubar a conexão: reconecta já, na mesma sessão.
        if (json.has("goAway") && resumeHandle != null && !reconnecting) {
            reconnecting = true
            val old = socket
            connect()
            old?.close(1000, null)
            return
        }

        if (json.has("setupComplete")) {
            if (reconnecting) {
                reconnecting = false // retomada: o áudio já está rodando
            } else {
                try {
                    startAudio()
                } catch (e: Exception) {
                    fail("Erro no áudio: ${e.message}")
                    return
                }
                status = VoiceStatus.Listening
                // Ela começa falando.
                socket?.send("""{"clientContent":{"turns":[{"role":"user","parts":[{"text":"Hi!"}]}],"turnComplete":true}}""")
            }
        }

        val content = json.optJSONObject("serverContent") ?: return
        if (content.optBoolean("interrupted")) stopPlayback()
        content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { append(it, Line.Role.Me) }
        content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { append(it, Line.Role.Tutor) }
        if (content.optBoolean("turnComplete")) openTutor = null
        val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data") ?: continue
            if (data.isNotEmpty()) play(Base64.decode(data, Base64.DEFAULT))
        }
    }

    /** A transcrição chega em pedaços: junta na fala aberta daquela pessoa. */
    private fun append(text: String, role: Line.Role) {
        val open = if (role == Line.Role.Me) openMine else openTutor
        if (open != null && open < lines.size && lines[open].role == role) {
            lines[open] = lines[open].copy(text = lines[open].text + text)
        } else {
            lines.add(Line(role, text))
            if (role == Line.Role.Me) openMine = lines.size - 1 else openTutor = lines.size - 1
        }
    }

    // endregion

    // region Áudio

    @SuppressLint("MissingPermission") // a tela pede o microfone antes de começar
    private fun startAudio() {
        // Microfone: 16 kHz, com cancelamento de eco do próprio Android.
        val micRate = 16000
        val minIn = AudioRecord.getMinBufferSize(micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, micRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 6400)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("microfone indisponível")
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        }
        recorder = rec
        rec.startRecording()
        micThread = Thread({ micLoop(rec) }, "mic").apply { start() }

        // Voz dela: 24 kHz, pelo alto-falante.
        val outRate = 24000
        val minOut = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(outRate)
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
        val chunk = ShortArray(640)
        val bytes = ByteArray(chunk.size * 2)
        while (!Thread.currentThread().isInterrupted) {
            val n = rec.read(chunk, 0, chunk.size)
            if (n <= 0) {
                if (n < 0) break
                continue
            }
            var sum = 0.0
            for (i in 0 until n) {
                val s = chunk[i]
                bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
                val f = s / 32768.0
                sum += f * f
            }
            val loud = sqrt(sum / n) > VOICE_THRESHOLD
            val data = Base64.encodeToString(bytes, 0, n * 2, Base64.NO_WRAP)
            val message = """{"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=16000","data":"$data"}}}"""
            // Só começa a enviar quando detecta voz; antes guarda um trechinho para não cortar o começo.
            val toSend: List<String> = synchronized(mic) {
                when {
                    !micOn -> emptyList()
                    micStarted -> listOf(message)
                    loud -> {
                        micStarted = true
                        val out = listOf("""{"realtimeInput":{"activityStart":{}}}""") + preroll + message
                        preroll.clear()
                        out
                    }
                    else -> {
                        preroll.addLast(message)
                        if (preroll.size > 8) preroll.removeFirst()
                        emptyList()
                    }
                }
            }
            val ws = socket
            toSend.forEach { ws?.send(it) }
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
        speakingUntil = maxOf(now, speakingUntil) + pcm.size / 2 * 1000L / 24000
        val until = speakingUntil
        main.postDelayed({
            if (status == VoiceStatus.Speaking && SystemClock.elapsedRealtime() >= speakingUntil && until == speakingUntil) {
                status = VoiceStatus.Listening
                audioLevel = 0f
            }
        }, until - now + 150)
    }

    /** Você apertou para falar (ou ela foi interrompida): corta a voz dela na hora. */
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
        /** Volume a partir do qual consideramos que é voz, e não silêncio. */
        private const val VOICE_THRESHOLD = 0.01

        /** Mensagens do Gemini (em inglês e técnicas) em português claro. */
        fun friendly(message: String): String {
            val m = message.lowercase()
            return when {
                "quota" in m || "resource_exhausted" in m || "rate limit" in m ->
                    "A cota gratuita do Gemini acabou por agora. Espere alguns minutos e tente de novo."
                "api key" in m || "api_key" in m || "permission" in m || "unauthenticated" in m ->
                    "O Gemini recusou sua chave. Confira a chave."
                "microfone" in m || "áudio" in m -> message
                "context" in m || "token" in m || "too long" in m || "exceed" in m || "deadline" in m || "session" in m ->
                    "A conversa ficou longa demais e o Gemini encerrou."
                else -> "A conexão com o Gemini caiu. Tente de novo."
            }
        }
    }
}
