package com.joabsonsouza.mytutor

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tela inicial: escolha do tutor, IA (na engrenagem) e começar/continuar (igual ao HomeView do iOS). */
@Composable
fun HomeScreen(
    maleVoice: Boolean,
    onPickTutor: (male: Boolean) -> Unit,
    hasSaved: Boolean,
    dueCards: Int,
    error: String?,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tutor = if (maleVoice) "peter" else "ana"
    val tutorName = if (maleVoice) "Peter" else "Ana"

    Column(
        Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding()
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Topo: logo e engrenagem.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo), "mytutor", modifier = Modifier.height(34.dp))
            Spacer(Modifier.weight(1f))
            CircleButton(Icons.Filled.Settings, "Configurações", onClick = onOpenSettings)
        }

        Hero(tutor, tutorName)

        Column {
            Text("Fale inglês", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp, lineHeight = 42.sp)
            Text("sem travar.", color = AppColors.yellow, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp, lineHeight = 42.sp)
        }

        // Quick fix.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.card)
                .drawWithContent {
                    drawContent()
                    drawRect(AppColors.yellow, size = size.copy(height = 3.dp.toPx()))
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Overline("Quick fix")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("I have 26 years", color = AppColors.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.LineThrough)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AppColors.yellow, modifier = Modifier.size(16.dp))
                Text("I'm 26 years old", color = AppColors.yellow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Com quem praticar.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Com quem você quer praticar?", color = AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TutorCard("ana", "Ana", "Voz feminina", !maleVoice, Modifier.weight(1f)) { onPickTutor(false) }
                TutorCard("peter", "Peter", "Voz masculina", maleVoice, Modifier.weight(1f)) { onPickTutor(true) }
            }
        }

        Spacer(Modifier.weight(1f))
        error?.let {
            Text(it, color = AppColors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        // Começar, flashcards e conversas salvas.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Começar com $tutorName", Modifier.weight(1f), icon = Icons.Filled.Mic, onClick = onStart)
            Box {
                CircleButton(Icons.Filled.ViewAgenda, if (dueCards > 0) "Flashcards, $dueCards para revisar" else "Flashcards",
                    size = 56.dp, iconSize = 22.dp, bordered = true, onClick = onOpenCards)
                if (dueCards > 0) {
                    Text("$dueCards", color = AppColors.background, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopEnd).heightIn(min = 20.dp).widthIn(min = 20.dp)
                            .clip(CircleShape).background(AppColors.yellow).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (hasSaved) {
                CircleButton(Icons.Filled.History, "Conversas salvas", size = 56.dp, iconSize = 22.dp, bordered = true, onClick = onContinue)
            }
        }
    }
}

/** Tutor recortado sobre um "sol" amarelo, com um balão de fala. */
@Composable
private fun Hero(tutor: String, tutorName: String) {
    Box(Modifier.fillMaxWidth().height(270.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.requiredSize(300.dp).offset(y = (-15).dp).border(1.dp, AppColors.yellow.copy(alpha = 0.25f), CircleShape))
            Box(Modifier.size(250.dp).clip(CircleShape).background(AppColors.yellow))
            Box(
                Modifier.size(250.dp, 270.dp).clip(RoundedCornerShape(bottomStart = 125.dp, bottomEnd = 125.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(painterResource(tutorImage(tutor)), tutorName, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.requiredSize(400.dp, 600.dp).offset(y = 165.dp))
            }
        }
        Text(
            "Hi ${Prompt.learnerName}! What do you want to practice today?",
            color = AppColors.background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 34.dp).rotate(3f)
                .shadow(14.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                .background(Color.White).widthIn(max = 174.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TutorCard(key: String, name: String, voice: String, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    Row(
        modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) AppColors.card else Color.Transparent)
            .border(2.dp, if (selected) AppColors.yellow else AppColors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onPick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TutorFace(key, 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(voice, color = AppColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
