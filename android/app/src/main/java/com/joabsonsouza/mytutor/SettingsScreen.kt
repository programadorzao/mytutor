package com.joabsonsouza.mytutor

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Configurações: perfil, qual IA usar e preferências da conversa (igual ao SettingsView do iOS). */
@Composable
fun SettingsScreen(
    keys: ApiKeys,
    conversations: List<SavedConversation>,
    onEditProfile: () -> Unit,
    onOpenKey: (Provider) -> Unit,
    onContinue: (SavedConversation) -> Unit,
    onDelete: (SavedConversation) -> Unit,
    onClose: () -> Unit,
) {
    var listenByDefault by remember { mutableStateOf(Settings.listenByDefault) }
    var showHistory by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleButton(Icons.Filled.ChevronLeft, "Voltar", iconSize = 26.dp, onClick = onClose)
            Text("Configurações", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
        }

        // Perfil.
        Row(Modifier.fillMaxWidth().card(18.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LearnerFace(56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(Profile.name.ifEmpty { "Sem nome" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                val labels = Interest.all.filter { it.id in Profile.interests }.map { it.label }
                Text(if (labels.isEmpty()) "Nenhum interesse escolhido" else labels.joinToString(" · "),
                    color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.heightIn(min = 44.dp).clip(CircleShape).border(2.dp, AppColors.border, CircleShape)
                    .clickable(onClick = onEditProfile).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Editar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        // IA.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Inteligência artificial")
            ProviderRow(
                title = "Gemini Live", tag = "Recomendado", highlightTag = true,
                detail = "Voz natural e corrige pronúncia. Chave grátis do Google, com limite diário.",
                hasKey = keys.gemini.isNotEmpty(), selected = true, available = true,
                onSelect = { if (keys.gemini.isEmpty()) onOpenKey(Provider.Gemini) }, onKey = { onOpenKey(Provider.Gemini) },
            )
            ProviderRow(
                title = "OpenAI Realtime", tag = "Em breve no Android", highlightTag = false,
                detail = "Voz natural e corrige pronúncia. Chave paga da OpenAI (precisa de crédito).",
                hasKey = keys.openAI.isNotEmpty(), selected = false, available = false,
                onSelect = { onOpenKey(Provider.OpenAI) }, onKey = { onOpenKey(Provider.OpenAI) },
            )
            Text("Cada pessoa usa a própria chave, que fica só neste celular. Toque em \"Chave\" para ver como pegar a sua.",
                color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        // Conversa.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Conversa")
            Column(Modifier.fillMaxWidth().card()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                        listenByDefault = !listenByDefault
                        Settings.listenByDefault = listenByDefault
                    }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Treinar o ouvido", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("O texto do tutor fica escondido. Toque para ver.", color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Switch(listenByDefault)
                }
                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(AppColors.border))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (conversations.isEmpty()) 0.45f else 1f)
                        .clickable(enabled = conversations.isNotEmpty()) { showHistory = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Conversas salvas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${conversations.size}", color = AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ChevronRight, null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Rodapé.
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.logo), null, modifier = Modifier.height(32.dp).alpha(0.6f))
            Text("Versão 1.0", color = AppColors.placeholder, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showHistory) {
        HistorySheet(conversations, onContinue = { showHistory = false; onContinue(it) }, onDelete = onDelete, onClose = { showHistory = false })
    }
}

@Composable
private fun ProviderRow(
    title: String, tag: String, highlightTag: Boolean, detail: String, hasKey: Boolean, selected: Boolean, available: Boolean,
    onSelect: () -> Unit, onKey: () -> Unit,
) {
    val on = selected
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (on) AppColors.card else Color.Transparent)
            .border(2.dp, if (on) AppColors.yellow else AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).alpha(if (available) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                val showTag = if (!hasKey && available) "Sem chave" else tag
                val yellow = highlightTag && hasKey
                Text(showTag, color = if (yellow) AppColors.background else AppColors.muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(CircleShape).background(if (yellow) AppColors.yellow else AppColors.border).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(detail, color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            Modifier.height(32.dp).clip(CircleShape).background(if (hasKey) AppColors.border else AppColors.yellow)
                .clickable(onClick = onKey).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Key, null, tint = if (hasKey) Color.White else AppColors.background, modifier = Modifier.size(14.dp))
            Text("Chave", color = if (hasKey) Color.White else AppColors.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val ring by animateDpAsState(if (on) 7.dp else 2.dp, label = "radio")
        Box(Modifier.size(22.dp).clip(CircleShape).border(ring, if (on) AppColors.yellow else AppColors.radioOff, CircleShape))
    }
}

/** Chave no estilo do app: amarela quando ligada. */
@Composable
private fun Switch(on: Boolean) {
    val x by animateDpAsState(if (on) 23.dp else 3.dp, label = "switch")
    Box(Modifier.width(50.dp).height(30.dp).clip(CircleShape).background(if (on) AppColors.yellow else AppColors.border)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(24.dp).clip(CircleShape).background(if (on) AppColors.background else Color.White))
    }
}

/** Lista das conversas salvas: tocar continua, a lixeira apaga (com confirmação). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    conversations: List<SavedConversation>,
    onContinue: (SavedConversation) -> Unit,
    onDelete: (SavedConversation) -> Unit,
    onClose: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedConversation?>(null) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(),
        containerColor = AppColors.background,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Conversas salvas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            Text("Fechar", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(AppColors.card).clickable(onClick = onClose)
                    .padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (conversations.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, null, tint = AppColors.muted, modifier = Modifier.size(34.dp))
                Text("Nenhuma conversa salva", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Durante uma conversa, toque no marcador para salvar.", color = AppColors.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.card)
                    .verticalScroll(rememberScrollState()),
            ) {
                conversations.forEachIndexed { index, c ->
                    if (index > 0) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(AppColors.border))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable { onContinue(c) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TutorFace(c.tutor, 44.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val ago = DateUtils.getRelativeTimeSpanString(c.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                Text("${if (c.tutor == "peter") "Peter" else "Ana"} · ${c.lines.size} falas · $ago",
                                    color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Box(Modifier.size(44.dp).clickable { pendingDelete = c }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Delete, "Apagar conversa", tint = AppColors.danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Apagar esta conversa?") },
            text = { Text("Não dá para desfazer.") },
            confirmButton = { TextButton({ onDelete(c); pendingDelete = null }) { Text("Apagar", color = AppColors.danger) } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancelar", color = Color.White) } },
            containerColor = AppColors.card, titleContentColor = Color.White, textContentColor = AppColors.muted,
        )
    }
}
