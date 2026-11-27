package com.joabsonsouza.mytutor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// region Criar cartão a partir de uma fala

private enum class CardMode(val title: String) { Word("Palavra"), Phrase("Expressão") }

/**
 * Escolha uma palavra (ou um trecho seguido, no modo Expressão), confira a tradução e adicione.
 * Dá para criar vários cartões da mesma frase, um de cada vez (igual ao NewCardView do iOS).
 */
@Composable
fun NewCardSheet(sentence: String, onClose: () -> Unit) {
    val words = remember(sentence) { sentence.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    var mode by remember { mutableStateOf(CardMode.Word) }
    /** Palavras marcadas (posição na frase). No modo Palavra, no máximo uma. */
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    val haptic = LocalHapticFeedback.current
    val canAdd = clean(front).isNotEmpty() && back.isNotBlank() && !translating

    fun clearSelection() { selected = emptySet(); front = ""; back = "" }

    // Tradução automática do verso sempre que a frente muda.
    LaunchedEffect(front) {
        val text = clean(front)
        if (text.isEmpty()) { back = ""; return@LaunchedEffect }
        translating = true
        back = runCatching { Translator.toPortuguese(text) }.getOrDefault(back)
        translating = false
    }

    /** Palavra: marca só a tocada. Expressão: cada toque marca ou desmarca aquela palavra, e o cartão junta as marcadas na ordem da frase. */
    fun tap(index: Int) {
        selected = when (mode) {
            CardMode.Word -> if (selected == setOf(index)) emptySet() else setOf(index)
            CardMode.Phrase -> if (index in selected) selected - index else selected + index
        }
        front = selected.sorted().joinToString(" ") { words[it] }
    }

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                if (added.isEmpty()) "Cancelar" else "Concluir", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterStart).clip(CircleShape).background(AppColors.card)
                    .clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text("Novo flashcard", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Segmented(CardMode.entries.map { it.title }, mode.ordinal) {
                mode = CardMode.entries[it]
                clearSelection()
            }
            Overline(
                when (mode) {
                    CardMode.Word -> "Toque numa palavra"
                    CardMode.Phrase -> "Toque nas palavras da expressão (ou em todas, para a frase inteira)"
                }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEachIndexed { index, word ->
                    val on = index in selected
                    Text(
                        word, color = if (on) AppColors.background else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(if (on) AppColors.yellow else AppColors.card)
                            .clickable { tap(index) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            CardField("Frente (inglês)", front) { front = it }
            CardField(if (translating) "Verso (traduzindo…)" else "Verso (tradução)", back) { back = it }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape)
                    .background(if (canAdd) AppColors.yellow else AppColors.border)
                    .clickable(enabled = canAdd) {
                        val card = clean(front)
                        FlashcardStore.add(Flashcard(front = card, back = back.trim(), example = sentence))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        added.add(card)
                        clearSelection()
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = if (canAdd) AppColors.background else AppColors.muted, modifier = Modifier.size(20.dp))
                Text("Adicionar cartão", color = if (canAdd) AppColors.background else AppColors.muted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }

            if (added.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline(if (added.size == 1) "1 cartão criado" else "${added.size} cartões criados")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        added.forEach { card ->
                            Row(
                                Modifier.clip(CircleShape).background(AppColors.card).padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Check, null, tint = AppColors.yellow, modifier = Modifier.size(14.dp))
                                Text(card, color = AppColors.yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Exemplo")
                Text(sentence, color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun CardField(title: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(title)
        BasicTextField(
            value, onChange,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(AppColors.yellow),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.card).padding(12.dp),
        )
    }
}

/** Tira pontuação das pontas ("today?" → "today"). */
private fun clean(text: String) = text.trim { it.isWhitespace() || !it.isLetterOrDigit() && it != '\'' && it != '-' }

// endregion

// region Revisão

/** Mostra os cartões do dia: tente lembrar, vire e diga se acertou. */
@Composable
fun FlashcardsScreen(onClose: () -> Unit) {
    val queue = remember { mutableStateListOf<Flashcard>().apply { addAll(FlashcardStore.due()) } }
    var flipped by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(FlashcardStore.all().size) }

    fun next() {
        flipped = false
        if (queue.isNotEmpty()) queue.removeAt(0)
    }

    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Filled.Close, "Fechar", iconSize = 16.dp, onClick = onClose)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Flashcards", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("${queue.size} para hoje · $total no total", color = AppColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        val card = queue.firstOrNull()
        if (card != null) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 260.dp).clip(RoundedCornerShape(20.dp)).background(AppColors.card)
                    .clickable { flipped = true }.animateContentSize(),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text(card.front, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    if (flipped) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.border))
                        Text(card.back, color = AppColors.yellow, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("“${card.example}”", color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(44.dp).clickable {
                        FlashcardStore.delete(card.id)
                        next()
                        total = FlashcardStore.all().size
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, "Apagar cartão", tint = AppColors.muted, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.weight(1f))
            if (flipped) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnswerButton("Errei", AppColors.danger, Modifier.weight(1f)) {
                        val updated = FlashcardStore.review(card, correct = false)
                        next()
                        queue.add(updated) // errou: volta para o fim da fila de hoje
                    }
                    AnswerButton("Acertei", AppColors.yellow, Modifier.weight(1f)) {
                        FlashcardStore.review(card, correct = true)
                        next()
                    }
                }
            } else {
                AnswerButton("Mostrar resposta", Color.White, Modifier.fillMaxWidth()) { flipped = true }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Verified, null, tint = AppColors.yellow, modifier = Modifier.size(52.dp))
                Text(if (total == 0) "Nenhum flashcard ainda" else "Tudo revisado por hoje!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (total == 0) "Na conversa, segure o dedo numa fala e toque em \"Criar flashcard\"." else "Volte amanhã para os próximos cartões.",
                    color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnswerButton(title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 56.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(title, color = AppColors.background, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

// endregion
