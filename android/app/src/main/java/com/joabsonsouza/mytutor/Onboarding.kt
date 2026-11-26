package com.joabsonsouza.mytutor

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Abertura com o logo, mostrada por um instante ao abrir o app. */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.symbol), null, modifier = Modifier.size(160.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = AppColors.yellow)) { append("my") }
                    withStyle(SpanStyle(color = Color.White)) { append("tutor") }
                },
                fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                modifier = Modifier.offset(y = (-20).dp),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 0.6f, 0.3f).forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.yellow.copy(alpha = it))) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Fale inglês sem travar.", color = AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private enum class OnboardingStep { Welcome, Profile, Interests, Key }

/** Boas-vindas → nome e foto → interesses → chave. Ao editar pela engrenagem, começa no nome. */
@Composable
fun OnboardingScreen(keys: ApiKeys, editing: Boolean = false, onFinish: () -> Unit) {
    var step by remember { mutableStateOf(if (editing) OnboardingStep.Profile else OnboardingStep.Welcome) }
    var name by remember { mutableStateOf(Profile.name) }
    var photo by remember { mutableStateOf(Profile.photo) }
    val interests = remember { mutableStateListOf<String>().apply { addAll(Profile.interests) } }
    val cleanName = name.trim()
    val total = if (editing) 2 else 3

    fun finish() {
        Profile.save(cleanName, photo, interests.toList())
        onFinish()
    }

    BackHandler(enabled = step != OnboardingStep.Welcome && !(editing && step == OnboardingStep.Profile)) {
        step = when (step) {
            OnboardingStep.Key -> OnboardingStep.Interests
            OnboardingStep.Interests -> OnboardingStep.Profile
            else -> OnboardingStep.Welcome
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding()) {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { current ->
            when (current) {
                OnboardingStep.Welcome -> Welcome { step = OnboardingStep.Profile }
                OnboardingStep.Profile -> ProfileStep(
                    editing = editing, total = total, name = name, onName = { name = it },
                    photo = photo, onPhoto = { photo = it },
                    onBack = { if (editing) onFinish() else step = OnboardingStep.Welcome },
                    onNext = { step = OnboardingStep.Interests },
                )
                OnboardingStep.Interests -> InterestsStep(
                    editing = editing, total = total, interests = interests,
                    onBack = { step = OnboardingStep.Profile },
                    onNext = { if (editing) finish() else step = OnboardingStep.Key },
                )
                OnboardingStep.Key -> KeyStep(keys = keys, total = total, onBack = { step = OnboardingStep.Interests }, onFinish = { finish() })
            }
        }
    }
}

// region Boas-vindas

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo), "mytutor", modifier = Modifier.height(44.dp))
        Spacer(Modifier.height(32.dp))
        Box(Modifier.height(340.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.size(340.dp).offset(y = 30.dp).border(1.dp, AppColors.yellow.copy(alpha = 0.22f), CircleShape))
            Box(Modifier.size(290.dp).clip(CircleShape).background(AppColors.yellow))
            // Os dois inteiros e um pouco sobrepostos no meio.
            Box(
                Modifier.size(290.dp, 330.dp).clip(RoundedCornerShape(bottomStart = 145.dp, bottomEnd = 145.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                TutorFigure("ana", (-62).dp)
                TutorFigure("peter", 62.dp)
            }
            Bubble("Hi there!", -4f, Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-30).dp))
            Bubble("Let's talk!", 4f, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-18).dp))
        }
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Fale inglês", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.3).sp, lineHeight = 44.sp)
            Text("sem travar.", color = AppColors.yellow, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.3).sp, lineHeight = 44.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Converse por voz com a Ana ou o Peter. Eles corrigem você no meio da conversa, sem parar o papo.",
            color = AppColors.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Vamos começar", arrow = true, onClick = onStart)
    }
}

@Composable
private fun TutorFigure(tutor: String, x: androidx.compose.ui.unit.Dp) {
    Image(
        painterResource(tutorImage(tutor)), null, contentScale = ContentScale.FillBounds,
        modifier = Modifier.offset(x = x, y = 7.5.dp).requiredSize(230.dp, 345.dp),
    )
}

@Composable
private fun Bubble(text: String, angle: Float, modifier: Modifier) {
    Text(
        text, color = AppColors.background, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.rotate(angle).shadow(14.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// endregion

// region Nome e foto

@Composable
private fun ProfileStep(
    editing: Boolean, total: Int, name: String, onName: (String) -> Unit,
    photo: Bitmap?, onPhoto: (Bitmap?) -> Unit, onBack: () -> Unit, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            onPhoto(bitmap.squareThumbnail())
        }
    }
    val cleanName = name.trim()

    Column(
        Modifier.fillMaxSize().clickable(indication = null, interactionSource = null) { focus.clearFocus() }
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header(1, total, closeIcon = editing, onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Title("Como o Tutor deve", Color.White)
                Title("te chamar?", AppColors.yellow)
            }
            Text("Ele usa seu nome para cumprimentar e puxar conversa.", color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(112.dp).clickable(indication = null, interactionSource = null) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                if (photo != null) {
                    Image(photo.asImageBitmap(), "Trocar foto", contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp).clip(CircleShape))
                } else {
                    Canvas(Modifier.size(112.dp).clip(CircleShape).background(AppColors.card)) {
                        drawCircle(AppColors.radioOff, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))), radius = size.minDimension / 2 - 1.dp.toPx())
                    }
                    Icon(Icons.Outlined.PhotoCamera, "Adicionar foto", tint = AppColors.muted, modifier = Modifier.size(34.dp).align(Alignment.Center))
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(AppColors.background).padding(3.dp)
                        .clip(CircleShape).background(AppColors.yellow),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, null, tint = AppColors.background, modifier = Modifier.size(18.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (photo == null) "Foto opcional · aparece nas suas falas" else "Toque para trocar a foto",
                    color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (photo != null) {
                    Text("Remover", color = AppColors.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onPhoto(null) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Seu nome")
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.card)
                    .border(2.dp, if (focused) AppColors.yellow else AppColors.border, RoundedCornerShape(14.dp))
                    .clickable { focusRequester.requestFocus() }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = name, onValueChange = onName, singleLine = true, textStyle = style,
                    cursorBrush = SolidColor(AppColors.yellow),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
                )
                if (name.isEmpty()) Text("Ex.: Joabson", style = style.copy(color = AppColors.placeholder))
            }
        }

        ChatPreview(cleanName, photo)

        Spacer(Modifier.weight(1f))
        if (cleanName.isEmpty()) DisabledButton("Digite seu nome") else PrimaryButton("Continuar", arrow = true, onClick = onNext)
    }
}

@Composable
private fun ChatPreview(name: String, photo: Bitmap?) {
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Assim vai aparecer no chat")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorFace("ana", 30.dp)
            Text("Hi ${name.ifEmpty { "…" }}! Nice to meet you.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                    .background(AppColors.border).padding(horizontal = 12.dp, vertical = 9.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Text("Nice to meet you too!", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp, topEnd = 14.dp))
                    .background(AppColors.myBubble).padding(horizontal = 12.dp, vertical = 9.dp))
            LearnerFace(30.dp, name, photo)
        }
    }
}

// endregion

// region Interesses

@Composable
private fun InterestsStep(editing: Boolean, total: Int, interests: MutableList<String>, onBack: () -> Unit, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(2, total, closeIcon = false, onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Title("Sobre o que você", Color.White)
                Title("gosta de falar?", AppColors.yellow)
            }
            Text("Escolha pelo menos ${Profile.MIN_INTERESTS}. O Tutor usa isso para sugerir assuntos.",
                color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        FlowRow(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Interest.all.forEach { interest ->
                val on = interest.id in interests
                Text(
                    interest.label,
                    color = if (on) AppColors.background else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape)
                        .background(if (on) AppColors.yellow else Color.Transparent)
                        .border(BorderStroke(2.dp, if (on) AppColors.yellow else AppColors.border), CircleShape)
                        .clickable { if (on) interests.remove(interest.id) else interests.add(interest.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val missing = Profile.MIN_INTERESTS - interests.size
            when {
                missing > 0 -> DisabledButton(if (missing == 1) "Escolha mais 1" else "Escolha mais $missing")
                editing -> PrimaryButton("Salvar", arrow = true, onClick = onNext)
                else -> PrimaryButton("Continuar", arrow = true, onClick = onNext)
            }
            Text("Dá para mudar depois na engrenagem.", color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// endregion

// region Chave

@Composable
private fun KeyStep(keys: ApiKeys, total: Int, onBack: () -> Unit, onFinish: () -> Unit) {
    var provider by remember { mutableStateOf(Provider.Gemini) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Header(3, total, closeIcon = false, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Title("Conecte a", Color.White)
                    Title("sua IA.", AppColors.yellow)
                }
                Text("A Ana e o Peter falam usando a sua própria chave. O Gemini é grátis e é o mais fácil para começar.",
                    color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Segmented(listOf("Gemini", "OpenAI"), if (provider == Provider.Gemini) 0 else 1) {
                provider = if (it == 0) Provider.Gemini else Provider.OpenAI
            }
            KeyContent(keys, provider) { saved -> if (saved) onFinish() }
        }
        Text("Pular por enquanto", color = AppColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onFinish).padding(vertical = 12.dp))
    }
}

/** Seletor de duas opções (equivalente ao Picker segmentado do iOS). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.card).padding(2.dp)) {
        options.forEachIndexed { i, title ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) Color(0xFF636366) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(title, color = Color.White, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

// endregion

// region Peças

@Composable
private fun Title(text: String, color: Color) {
    Text(text, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.9).sp, lineHeight = 34.sp)
}

@Composable
private fun Header(step: Int, total: Int, closeIcon: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircleButton(if (closeIcon) Icons.Filled.Close else Icons.Filled.ChevronLeft, if (closeIcon) "Fechar" else "Voltar", onClick = onBack)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..total).forEach { n ->
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (n <= step) AppColors.yellow else AppColors.border))
            }
        }
        Text("$step de $total", color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// endregion
