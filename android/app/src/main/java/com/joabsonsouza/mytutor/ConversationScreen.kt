package com.joabsonsouza.mytutor

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Tradução de uma fala do tutor: a fala em si e cada correção, na mesma ordem. */
data class Translated(val main: String, val fixes: List<String>)

private val bubbleSize = 150.dp
private val bottomBarHeight = 170.dp

/** Tela de conversa: chat por trás, tutor flutuante (arrastável) e botão de segurar para falar (igual ao iOS). */
@Composable
fun ConversationScreen(
    tutor: String,
    tutorName: String,
    providerName: String,
    lines: List<Line>,
    status: VoiceStatus,
    error: String?,
    mouth: Double,
    holding: Boolean,
    isSaved: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onSave: () -> Unit,
    onEnd: () -> Unit,
) {
    // Modo escuta: desfoca as falas do tutor. Começa como está nas Configurações.
    var listeningMode by remember { mutableStateOf(Settings.listenByDefault) }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val translations = remember { mutableStateMapOf<String, Translated>() }
    var cardSource by remember { mutableStateOf<Line?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun translate(line: Line) {
        scope.launch {
            val parts = Correction.split(line.text)
            translations[line.id] = try {
                Translated(
                    main = if (parts.main.isEmpty()) "" else Translator.toPortuguese(parts.main),
                    fixes = parts.fixes.map { Translator.toPortuguese(it) },
                )
            } catch (_: Exception) {
                Translated("Não foi possível traduzir agora.", emptyList())
            }
        }
    }

    fun openCard(line: Line) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        cardSource = line
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding()) {
        val width = maxWidth
        val height = maxHeight
        Column(Modifier.fillMaxSize()) {
            TopBar(tutorName, providerName, lines.size, listeningMode, isSaved, lines.isEmpty(),
                onToggleListen = { listeningMode = !listeningMode; revealed.clear() }, onSave = onSave, onEnd = onEnd)

            val listState = rememberLazyListState()
            LaunchedEffect(lines.size, lines.lastOrNull()?.text) {
                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(lines, key = { it.id }) { line ->
                    if (line.role == Line.Role.Tutor) {
                        TutorMessage(
                            line = line, tutor = tutor, tutorName = tutorName,
                            listeningMode = listeningMode, revealed = revealed[line.id] == true,
                            translation = translations[line.id],
                            onToggleReveal = { revealed[line.id] = revealed[line.id] != true },
                            onTranslate = { translate(line) },
                            onHideTranslation = { translations.remove(line.id) },
                            onCard = { openCard(line) },
                        )
                    } else {
                        MyMessage(line.text.trim()) { openCard(line) }
                    }
                }
                // Fim da lista: espaço do tamanho da barra de baixo, para a última mensagem ficar acima do microfone.
                item { Spacer(Modifier.height(bottomBarHeight + 40.dp)) }
            }
        }

        BottomBar(
            tutor, tutorName, status, error, holding, onPress, onRelease,
            Modifier.align(Alignment.BottomCenter),
        )

        FloatingTutor(tutor, mouth, status, holding, width.value, height.value)
    }

    cardSource?.let { line ->
        NewCardSheet(sentence = line.text.trim(), onClose = { cardSource = null })
    }
}

@Composable
private fun TopBar(
    tutorName: String, providerName: String, count: Int, listeningMode: Boolean, isSaved: Boolean, empty: Boolean,
    onToggleListen: () -> Unit, onSave: () -> Unit, onEnd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(AppColors.background).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).clickable(onClick = onEnd), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronLeft, "Encerrar e voltar ao início", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tutorName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("$providerName · $count falas", color = AppColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        CircleButton(
            if (listeningMode) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
            if (listeningMode) "Mostrar falas do tutor" else "Modo escuta: desfocar falas do tutor",
            background = if (listeningMode) AppColors.yellow else AppColors.card,
            tint = if (listeningMode) AppColors.background else Color.White,
            onClick = onToggleListen,
        )
        CircleButton(
            if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            if (isSaved) "Conversa salva" else "Salvar para continuar depois",
            tint = if (isSaved) AppColors.yellow else Color.White,
            enabled = !empty && !isSaved,
            onClick = onSave,
        )
        Box(
            Modifier.height(44.dp).clip(CircleShape).background(AppColors.card).clickable(onClick = onEnd).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Encerrar", color = AppColors.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TutorMessage(
    line: Line, tutor: String, tutorName: String, listeningMode: Boolean, revealed: Boolean, translation: Translated?,
    onToggleReveal: () -> Unit, onTranslate: () -> Unit, onHideTranslation: () -> Unit, onCard: () -> Unit,
) {
    val parts = Correction.split(line.text)
    val hidden = listeningMode && !revealed
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 28.dp)) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                indication = null, interactionSource = null,
                onClick = { if (listeningMode) onToggleReveal() },
                onLongClick = { menu = true },
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorFace(tutor, 28.dp)
                Overline(tutorName)
                if (listeningMode) {
                    Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = AppColors.muted, modifier = Modifier.size(13.dp))
                }
            }
            Box(contentAlignment = Alignment.Center) {
                val blurred = if (hidden) {
                    if (Build.VERSION.SDK_INT >= 31) Modifier.blur(7.dp) else Modifier.alpha(0.04f)
                } else Modifier
                Column(blurred, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (parts.main.isNotEmpty()) {
                        Column(
                            Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                                .background(AppColors.card).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(parts.main, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                            translation?.main?.takeIf { it.isNotEmpty() }?.let { TranslationText(it) }
                        }
                    }
                    if (parts.fixes.isNotEmpty()) {
                        Row(Modifier.padding(start = 10.dp).height(IntrinsicSize.Min)) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(AppColors.yellow))
                            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Overline("Quick fix", AppColors.yellow)
                                parts.fixes.forEachIndexed { index, fix ->
                                    Text("“$fix”", color = Color(0xFFDAE0E3), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
                                    translation?.fixes?.getOrNull(index)?.let { TranslationText(it) }
                                }
                            }
                        }
                    }
                }
                if (hidden) {
                    Row(
                        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onToggleReveal).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Toque para ver", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
            if (translation == null) {
                MenuItem("Traduzir", Icons.Filled.Translate) { menu = false; onTranslate() }
            } else {
                MenuItem("Esconder tradução", Icons.Outlined.VisibilityOff) { menu = false; onHideTranslation() }
            }
            MenuItem("Criar flashcard", Icons.Filled.LibraryAdd) { menu = false; onCard() }
        }
    }
}

@Composable
private fun MenuItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = Color.White, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, null, tint = Color.White) },
        onClick = onClick,
    )
}

@Composable
private fun TranslationText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Translate, null, tint = AppColors.muted, modifier = Modifier.size(15.dp).padding(top = 2.dp))
        Text(text, color = AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyMessage(text: String, onCard: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 48.dp), contentAlignment = Alignment.CenterEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Overline(Prompt.learnerName)
                LearnerFace(28.dp)
            }
            Text(
                text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .background(AppColors.myBubble)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
            MenuItem("Criar flashcard", Icons.Filled.LibraryAdd) { menu = false; onCard() }
        }
    }
}

/** Tutor flutuante: arraste para mover; ao soltar, gruda no lado mais próximo. */
@Composable
private fun FloatingTutor(tutor: String, mouth: Double, status: VoiceStatus, holding: Boolean, widthDp: Float, heightDp: Float) {
    val density = LocalDensity.current
    val r = bubbleSize.value / 2
    val x = remember { Animatable(widthDp - r - 14) }
    val y = remember { Animatable(64 + r) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(if (dragging) 1.04f else 1f, label = "drag")
    val speaking = status == VoiceStatus.Speaking

    Box(
        Modifier
            .offset { IntOffset(((x.value - r) * density.density).roundToInt(), ((y.value - r) * density.density).roundToInt()) }
            .size(bubbleSize)
            .scale(scale)
            .pointerInput(widthDp, heightDp) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val target = if (x.value < widthDp / 2) r + 14 else widthDp - r - 14
                        scope.launch { x.animateTo(target, tween(240)) }
                    },
                    onDragCancel = { dragging = false },
                ) { change, drag ->
                    change.consume()
                    scope.launch {
                        x.snapTo((x.value + drag.x / density.density).coerceIn(r + 8, widthDp - r - 8))
                        y.snapTo((y.value + drag.y / density.density).coerceIn(60 + r, heightDp - bottomBarHeight.value - r))
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().shadow(20.dp, CircleShape).clip(CircleShape).background(AppColors.background)
                .border(3.dp, AppColors.yellow.copy(alpha = if (speaking) 0.6f else 0f), CircleShape).padding(4.dp),
        ) {
            Avatar(tutor, mouth, bubbleSize - 8.dp)
        }
        if (speaking && !holding) {
            Row(
                Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).height(28.dp).clip(CircleShape)
                    .background(AppColors.background).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf(9.0, 13.0, 7.0).forEach { h ->
                    Box(Modifier.width(3.dp).height((5 + mouth * h).dp).clip(CircleShape).background(AppColors.yellow))
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    tutor: String, tutorName: String, status: VoiceStatus, error: String?, holding: Boolean,
    onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier,
) {
    val hint = when {
        holding -> "Ouvindo… solte para enviar"
        status == VoiceStatus.Speaking -> "$tutorName está falando · segure para interromper"
        status == VoiceStatus.Listening -> "Segure para falar"
        else -> "Conectando…"
    }
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to AppColors.background.copy(alpha = 0f), 0.34f to AppColors.background))
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.border(2.dp, AppColors.yellow, CircleShape).padding(4.dp)) { TutorFace(tutor, 42.dp) }
            Overline(tutorName)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let {
                Text(it, color = AppColors.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3)
            }
            Text(hint, color = if (holding) AppColors.yellow else AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            MicButton(holding, status, onPress, onRelease)
        }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LearnerFace(46.dp)
            Overline("Você")
        }
    }
}

@Composable
private fun MicButton(holding: Boolean, status: VoiceStatus, onPress: () -> Unit, onRelease: () -> Unit) {
    val scale by animateFloatAsState(if (holding) 1.08f else 1f, tween(120), label = "mic")
    Box(
        Modifier.size(88.dp).scale(scale).alpha(if (status == VoiceStatus.Connecting) 0.4f else 1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (holding) PulseRing()
        Box(
            Modifier.size(88.dp).shadow(if (holding) 0.dp else 15.dp, CircleShape).clip(CircleShape)
                .background(if (holding) AppColors.yellow else Color.White),
        )
        Icon(Icons.Filled.Mic, "Segure para falar", tint = AppColors.background, modifier = Modifier.size(34.dp))
    }
}

/** Anel amarelo que pulsa atrás do microfone enquanto você fala. */
@Composable
private fun PulseRing() {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(88.dp).scale(1f + 0.55f * p).alpha(0.55f * (1 - p)).clip(CircleShape).background(AppColors.yellow))
}
