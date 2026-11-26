package com.joabsonsouza.mytutor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

/** Sem chave, abre o cadastro da chave (cada pessoa usa a sua). Com chave, vai para a conversa. */
@Composable
fun App() {
    val context = LocalContext.current
    val keys = remember { ApiKeys(context.applicationContext) }
    var editingKey by remember { mutableStateOf(false) }
    if (keys.gemini.isEmpty() || editingKey) {
        KeyScreen(
            keys = keys,
            provider = Provider.Gemini,
            onClose = if (keys.gemini.isEmpty()) null else ({ editingKey = false }),
            onSaved = { editingKey = false },
        )
    } else {
        VoiceTestScreen(apiKey = keys.gemini, onOpenKey = { editingKey = true })
    }
}

/** Etapa 2: começar/encerrar, segurar para falar e ver o texto das falas. A tela final vem nas próximas etapas. */
@Composable
fun VoiceTestScreen(apiKey: String, onOpenKey: () -> Unit) {
    val context = LocalContext.current
    val gemini = remember { GeminiLive() }
    DisposableEffect(Unit) { onDispose { gemini.stop() } }

    fun start() = gemini.start(
        apiKey = apiKey,
        instructions = "You are Ana, a friendly English teacher for a Brazilian learner. Start with a short greeting and ask what he wants to talk about. Keep replies short and correct his mistakes kindly.",
        voice = "Kore",
        history = emptyList(),
    )

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else gemini.error = "Permita o microfone para conversar."
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = AppColors.yellow)) { append("my") }
                withStyle(SpanStyle(color = Color.White)) { append("tutor") }
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(statusText(gemini.status), color = AppColors.muted, fontSize = 14.sp)
        gemini.error?.let {
            Text(it, color = AppColors.danger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }

        val listState = rememberLazyListState()
        LaunchedEffect(gemini.lines.size, gemini.lines.lastOrNull()?.text) {
            if (gemini.lines.isNotEmpty()) listState.animateScrollToItem(gemini.lines.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(gemini.lines, key = { it.id }) { line ->
                val mine = line.role == Line.Role.Me
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        line.text.trim(),
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (mine) AppColors.myBubble else AppColors.card)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (gemini.status == VoiceStatus.Idle) {
            PillButton("Começar com a Ana", AppColors.yellow, AppColors.background) {
                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (ok) start() else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
            Spacer(Modifier.height(8.dp))
            Text("Chave do Gemini", color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onOpenKey).padding(12.dp))
        } else {
            HoldToTalk(
                enabled = gemini.status == VoiceStatus.Listening || gemini.status == VoiceStatus.Speaking,
                onPress = { gemini.pressToTalk() },
                onRelease = { gemini.releaseToTalk() },
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Encerrar", AppColors.card, Color.White) { gemini.stop() }
        }
    }
}

private fun statusText(status: VoiceStatus) = when (status) {
    VoiceStatus.Idle -> "Toque em começar"
    VoiceStatus.Connecting -> "Conectando…"
    VoiceStatus.Listening -> "Segure o botão para falar"
    VoiceStatus.Speaking -> "Ana está falando…"
}

/** Botão redondo: enquanto o dedo está nele, o microfone fica ligado. */
@Composable
private fun HoldToTalk(enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(if (holding) AppColors.yellow else if (enabled) AppColors.card else AppColors.border)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    holding = true
                    onPress()
                    tryAwaitRelease()
                    holding = false
                    onRelease()
                })
            },
    ) {
        Text(
            if (holding) "Falando" else "Segure",
            color = if (holding) AppColors.background else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PillButton(title: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Text(title, color = foreground, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}
