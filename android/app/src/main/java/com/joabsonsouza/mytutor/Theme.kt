package com.joabsonsouza.mytutor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Mesmas cores do app iOS (ios/EnglishTutor/Theme.swift). */
object AppColors {
    val background = Color(0xFF1D1D1B)
    val card = Color(0xFF2B2B29)
    val border = Color(0xFF3A3A37)
    val yellow = Color(0xFFFFCC00)
    val muted = Color(0xFFA7B3B7)
    val myBubble = Color(0xFF3C5185)
    val myAvatar = Color(0xFF4A65A3)
    val danger = Color(0xFFFF8A80)
    val placeholder = Color(0xFF7D8A8E)
    val radioOff = Color(0xFF5A5A55)
}

/** Foto do tutor: "ana" ou "peter" + quadro closed/half/open/blink. */
fun tutorImage(tutor: String, frame: String = "closed"): Int = when ("${tutor}_$frame") {
    "ana_half" -> R.drawable.ana_half
    "ana_open" -> R.drawable.ana_open
    "ana_blink" -> R.drawable.ana_blink
    "peter_closed" -> R.drawable.peter_closed
    "peter_half" -> R.drawable.peter_half
    "peter_open" -> R.drawable.peter_open
    "peter_blink" -> R.drawable.peter_blink
    else -> R.drawable.ana_closed
}

/** Rosto do tutor recortado num círculo amarelo (igual ao TutorFace do iOS). */
@Composable
fun TutorFace(tutor: String, size: Dp, frame: String = "closed") {
    Box(
        Modifier.size(size).clip(CircleShape).background(AppColors.yellow),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(tutorImage(tutor, frame)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            // Foto 2x a largura e 3x a altura do círculo, descida para o rosto ficar no centro.
            modifier = Modifier.requiredSize(size * 2, size * 3).offset(y = size * 0.84f),
        )
    }
}

/** Tutor com boca animada e piscando sozinho (igual ao Avatar do iOS). `mouth` vai de 0 a 1. */
@Composable
fun Avatar(tutor: String, mouth: Double, size: Dp) {
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2500 + Random.nextInt(2500)).toLong())
            blinking = true
            delay(120)
            blinking = false
        }
    }
    val frame = when {
        mouth < 0.08 -> if (blinking) "blink" else "closed"
        mouth < 0.45 -> "half"
        else -> "open"
    }
    TutorFace(tutor, size, frame)
}

/** Sua foto, ou a inicial do nome num círculo azul. */
@Composable
fun LearnerFace(size: Dp, name: String = Profile.name, photo: Bitmap? = Profile.photo) {
    Box(
        Modifier.size(size).clip(CircleShape).background(AppColors.myAvatar),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                if (name.isBlank()) "?" else name.take(1).uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Rótulo pequeno em maiúsculas. */
@Composable
fun Overline(text: String, color: Color = AppColors.muted) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
}

val ScreenPadding = 20.dp
