package com.joabsonsouza.mytutor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Botão amarelo principal (cápsula com sombra amarela), com seta opcional. */
@Composable
fun PrimaryButton(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, arrow: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(9.dp, CircleShape, ambientColor = AppColors.yellow.copy(alpha = 0.35f), spotColor = AppColors.yellow.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(AppColors.yellow)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = AppColors.background, modifier = Modifier.size(20.dp))
        Text(title, color = AppColors.background, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        if (arrow) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AppColors.background, modifier = Modifier.size(20.dp))
    }
}

/** Botão cinza apagado, que explica o que falta ("Digite seu nome"). */
@Composable
fun DisabledButton(title: String) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(AppColors.border),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = AppColors.muted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Botão redondo com ícone (voltar, engrenagem, histórico…). */
@Composable
fun CircleButton(
    icon: ImageVector,
    description: String,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
    background: Color = AppColors.card,
    tint: Color = Color.White,
    bordered: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (bordered) Modifier.border(2.dp, AppColors.border, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Cartão escuro arredondado. */
fun Modifier.card(radius: Dp = 16.dp) = this.clip(RoundedCornerShape(radius)).background(AppColors.card)
