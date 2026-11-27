package com.joabsonsouza.mytutor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppData.init(applicationContext)
        setContent { App() }
    }
}

private enum class Screen { Splash, Onboarding, Home, Conversation }

/** Tela inicial quando parado; tela de conversa durante a sessão (igual ao ContentView do iOS). */
@Composable
fun App() {
    val context = LocalContext.current
    val keys = remember { ApiKeys(context.applicationContext) }
    val geminiLive = remember { GeminiLive() }
    val openAI = remember { OpenAIRealtime() }
    DisposableEffect(Unit) { onDispose { geminiLive.stop(); openAI.stop() } }
    var provider by remember { mutableStateOf(Settings.provider) }
    /** A IA escolhida. */
    val session: VoiceSession = if (provider == Provider.OpenAI) openAI else geminiLive
    val currentSession by rememberUpdatedState(session)

    var showSplash by remember { mutableStateOf(true) }
    var maleVoice by remember { mutableStateOf(Settings.maleVoice) }
    var conversations by remember { mutableStateOf(Storage.all()) }
    /** Id da conversa atual no histórico (salvar de novo atualiza a mesma entrada). */
    var currentId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var holding by remember { mutableStateOf(false) }
    var mouth by remember { mutableDoubleStateOf(0.0) }
    var dueCards by remember { mutableStateOf(FlashcardStore.due().size) }

    // Telas por cima.
    var keyFor by remember { mutableStateOf<Provider?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var pendingHistory by remember { mutableStateOf<List<Line>?>(null) }

    val tutor = if (maleVoice) "peter" else "ana"
    val tutorName = if (maleVoice) "Peter" else "Ana"
    val status = session.status

    fun begin(history: List<Line>) {
        // Sem chave da IA escolhida: abre o cadastro da chave.
        val key = keys.key(provider)
        if (key.isEmpty()) {
            keyFor = provider
            return
        }
        val voice = when (provider) {
            Provider.Gemini -> if (maleVoice) "Puck" else "Kore"
            Provider.OpenAI -> if (maleVoice) "cedar" else "marin"
        }
        session.start(key, Prompt.instructions(tutorName, history), voice, history)
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val history = pendingHistory ?: emptyList()
        pendingHistory = null
        if (granted) begin(history) else session.error = "Permita o microfone nas configurações do celular para conversar."
    }

    fun start(history: List<Line>) {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) begin(history) else {
            pendingHistory = history
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun continueConversation(c: SavedConversation) {
        showHistory = false
        showSettings = false
        maleVoice = c.tutor == "peter"
        Settings.maleVoice = maleVoice
        currentId = c.id
        start(c.lines)
    }

    fun saveCurrent() {
        Storage.save(SavedConversation(id = currentId, savedAt = System.currentTimeMillis(), tutor = tutor, lines = session.lines.toList()))
        conversations = Storage.all()
    }

    LaunchedEffect(Unit) {
        delay(1200)
        showSplash = false
    }

    // A cada ~60 ms aproxima a boca do volume da voz dela.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            val target = if (currentSession.status == VoiceStatus.Speaking) {
                val level = currentSession.audioLevel
                if (level > 0f) minOf(1.0, level * 5.0) else Random.nextDouble(0.25, 0.85)
            } else 0.0
            mouth += (target - mouth) * 0.55
        }
    }

    // A conversa caiu com erro: salva sozinha para dar para continuar depois.
    LaunchedEffect(session.error) {
        if (session.error != null && session.lines.isNotEmpty()) saveCurrent()
    }
    // Ao voltar de uma conversa, atualiza o número de cartões para hoje.
    LaunchedEffect(status == VoiceStatus.Idle) { dueCards = FlashcardStore.due().size }

    val screen = when {
        showSplash -> Screen.Splash
        !Profile.onboarded -> Screen.Onboarding
        status == VoiceStatus.Idle -> Screen.Home
        else -> Screen.Conversation
    }

    Box(Modifier.fillMaxSize().background(AppColors.background)) {
        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { current ->
            when (current) {
                Screen.Splash -> SplashScreen()
                Screen.Onboarding -> OnboardingScreen(keys) { }
                Screen.Home -> HomeScreen(
                    maleVoice = maleVoice,
                    onPickTutor = { maleVoice = it; Settings.maleVoice = it },
                    hasSaved = conversations.isNotEmpty(),
                    dueCards = dueCards,
                    error = session.error,
                    onStart = {
                        currentId = UUID.randomUUID().toString()
                        start(emptyList())
                    },
                    onContinue = { showHistory = true },
                    onOpenCards = { showCards = true },
                    onOpenSettings = { showSettings = true },
                )
                Screen.Conversation -> {
                    BackHandler { holding = false; session.stop() }
                    ConversationScreen(
                        tutor = tutor,
                        tutorName = tutorName,
                        providerName = if (provider == Provider.OpenAI) "OpenAI" else "Gemini",
                        lines = session.lines,
                        status = status,
                        error = session.error,
                        mouth = mouth,
                        holding = holding,
                        isSaved = conversations.firstOrNull { it.id == currentId }?.lines?.map { it.text } == session.lines.map { it.text },
                        onPress = {
                            if (!holding && (status == VoiceStatus.Listening || status == VoiceStatus.Speaking)) {
                                holding = true
                                session.pressToTalk()
                            }
                        },
                        onRelease = {
                            if (holding) {
                                holding = false
                                session.releaseToTalk()
                            }
                        },
                        onSave = { saveCurrent() },
                        onEnd = { holding = false; session.stop() },
                    )
                }
            }
        }

        if (showSettings) {
            SettingsScreen(
                keys = keys,
                provider = provider,
                onPickProvider = { provider = it; Settings.provider = it },
                conversations = conversations,
                onEditProfile = { editingProfile = true },
                onOpenKey = { keyFor = it },
                onContinue = { continueConversation(it) },
                onDelete = { Storage.delete(it.id); conversations = Storage.all() },
                onClose = { showSettings = false },
            )
        }
        if (editingProfile) {
            BackHandler { editingProfile = false }
            OnboardingScreen(keys, editing = true) { editingProfile = false }
        }
        if (showCards) {
            FlashcardsScreen { showCards = false; dueCards = FlashcardStore.due().size }
        }
        keyFor?.let { option ->
            BackHandler { keyFor = null }
            // Depois de salvar, essa IA já fica escolhida (igual ao iOS).
            KeySheet(keys, option, onSaved = { provider = option; Settings.provider = option }, onClose = { keyFor = null })
        }
    }

    if (showHistory) {
        HistorySheet(
            conversations,
            onContinue = { continueConversation(it) },
            onDelete = { Storage.delete(it.id); conversations = Storage.all() },
            onClose = { showHistory = false },
        )
    }
}
