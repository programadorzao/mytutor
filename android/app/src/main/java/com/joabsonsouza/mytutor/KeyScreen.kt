package com.joabsonsouza.mytutor

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A tela de chave como folha (tela inicial e Configurações): "Fechar" + título, igual ao iOS. */
@Composable
fun KeySheet(keys: ApiKeys, provider: Provider, onSaved: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Fechar", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterStart).clip(CircleShape).background(AppColors.card)
                    .clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                "Chave do ${if (provider == Provider.OpenAI) "OpenAI" else "Gemini"}",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(ScreenPadding)) {
            KeyContent(keys, provider) { saved -> if (saved) onSaved(); onClose() }
        }
    }
}

/** Cadastro da chave: aviso de privacidade, passo a passo com links e o campo para colar. */
@Composable
fun KeyContent(keys: ApiKeys, provider: Provider, onDone: (saved: Boolean) -> Unit) {
    val google = provider != Provider.OpenAI
    val current = keys.key(provider)
    var key by remember(provider) { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var problem by remember(provider) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current
    val cleanKey = key.trim()

    fun save() {
        if (cleanKey.isEmpty() || checking) return
        focus.clearFocus()
        typing = false
        checking = true
        scope.launch {
            problem = withContext(Dispatchers.IO) { ApiKeys.check(cleanKey, provider) }
            checking = false
            if (problem == null) {
                keys.save(cleanKey, provider)
                key = ""
                onDone(true)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Aviso de privacidade.
        Row(Modifier.fillMaxWidth().card().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Shield, null, tint = AppColors.yellow, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sua chave é só sua", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "O app é grátis e não tem servidor: ele fala direto com ${if (google) "o Google" else "a OpenAI"} usando a sua chave. " +
                        "Ela fica guardada só neste celular, protegida pelo Android. Nós nunca vemos nem guardamos sua chave, " +
                        "e qualquer custo de uso aparece na sua conta ${if (google) "do Google" else "da OpenAI"}.",
                    color = AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }

        // Passo a passo.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Como pegar sua chave")
            steps(google).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(AppColors.yellow), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = AppColors.background, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(step.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (step.url != null && step.linkTitle != null) {
                            Row(
                                Modifier.clickable { uri.openUri(step.url) },
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AppColors.yellow, modifier = Modifier.size(16.dp))
                                Text(step.linkTitle, color = AppColors.yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text(
                if (google) "O Gemini tem uso grátis com limite diário. Não precisa de cartão."
                else "A OpenAI cobra pelo uso (a partir de US$ 5 de crédito). Uma conversa de 10 minutos costuma custar centavos de dólar.",
                color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // Campo da chave.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(if (current.isEmpty()) "Sua chave" else "Trocar a chave (já tem uma salva)")
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.card)
                    .border(2.dp, AppColors.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    val style = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    // Fora da digitação, a chave aparece escondida ("AIza••••••••XYZ9").
                    BasicTextField(
                        value = if (typing || key.isEmpty()) key else masked(cleanKey),
                        onValueChange = { if (typing) { key = it; problem = null } },
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(AppColors.yellow),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { save() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
                    )
                    if (key.isEmpty() && !typing) Text("Cole a chave aqui", style = style.copy(color = AppColors.placeholder))
                }
                if (key.isEmpty()) {
                    Text(
                        "Colar", color = AppColors.yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            clipboard.getText()?.text?.let { key = it; problem = null; typing = false }
                        }.padding(8.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Cancel, "Limpar chave", tint = AppColors.muted,
                        modifier = Modifier.size(32.dp).clickable { key = ""; problem = null }.padding(6.dp),
                    )
                }
            }
            problem?.let { Text(it, color = AppColors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
                    .background(if (cleanKey.isEmpty()) AppColors.border else AppColors.yellow)
                    .clickable(enabled = cleanKey.isNotEmpty() && !checking) { save() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checking) {
                    CircularProgressIndicator(color = AppColors.background, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (checking) "Testando a chave…" else "Salvar chave",
                    color = if (cleanKey.isEmpty()) AppColors.muted else AppColors.background,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        if (current.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { keys.save("", provider); onDone(false) }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = AppColors.danger, modifier = Modifier.size(18.dp))
                Text("Apagar a chave salva", color = AppColors.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class GuideStep(val text: String, val linkTitle: String? = null, val url: String? = null)

private fun steps(google: Boolean) = if (google) listOf(
    GuideStep("Abra o Google AI Studio e entre com sua conta Google.", "Abrir o Google AI Studio", "https://aistudio.google.com/apikey"),
    GuideStep("Toque em \"Create API key\" (Criar chave de API). Se pedir, aceite os termos e escolha ou crie um projeto."),
    GuideStep("Copie a chave que aparecer (começa com \"AIza\"), volte aqui e cole abaixo."),
) else listOf(
    GuideStep("Crie sua conta (ou entre) na plataforma da OpenAI. É diferente da assinatura do ChatGPT.", "Abrir a plataforma da OpenAI", "https://platform.openai.com/signup"),
    GuideStep("Adicione crédito na sua conta. Sem crédito a voz não funciona.", "Abrir a página de pagamento", "https://platform.openai.com/settings/organization/billing/overview"),
    GuideStep("Crie uma chave em \"Create new secret key\" e copie (começa com \"sk-\"). Ela só aparece uma vez.", "Abrir a página de chaves", "https://platform.openai.com/api-keys"),
    GuideStep("Volte aqui e cole a chave abaixo."),
)

/** Só o começo e o fim da chave aparecem. */
private fun masked(k: String) =
    if (k.length <= 8) "•".repeat(k.length) else k.take(4) + "•".repeat(8) + k.takeLast(4)
