package com.joabsonsouza.mytutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Splash() }
    }
}

/** Etapa 1: só a abertura com o nome, para ver que o app roda. */
@Composable
fun Splash() {
    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = AppColors.yellow)) { append("my") }
                withStyle(SpanStyle(color = Color.White)) { append("tutor") }
            },
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text("Fale inglês sem travar.", color = AppColors.muted, fontSize = 14.sp)
    }
}
