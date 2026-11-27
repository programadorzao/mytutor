# Arquitetura

Como o mytutor funciona por dentro. Vale para as duas plataformas: o Android é uma cópia fiel do iOS, tela por tela.

## Visão geral

```
┌──────────── celular ────────────┐
│  Telas (SwiftUI / Compose)      │
│        │                        │
│  Sessão de voz ──── microfone   │
│  (Gemini ou OpenAI) ─ alto-falante
│        │                        │        ┌──────────────────┐
│  Chave (Keychain / Keystore) ───┼──────► │ Google / OpenAI  │
│  Dados (UserDefaults / Prefs)   │        └──────────────────┘
└─────────────────────────────────┘
```

Não existe servidor. Tudo o que o app guarda (perfil, conversas, flashcards, chaves) fica no aparelho.

## Fluxo das telas

1. **Abertura** com o logo, por ~1 s.
2. **Onboarding** (só na primeira vez): boas-vindas → nome e foto → interesses → chave da IA (dá para pular).
3. **Tela inicial**: escolhe Ana ou Peter e toca em "Começar". Sem chave da IA escolhida, abre o cadastro da chave.
4. **Conversa**: enquanto a sessão de voz está ativa. "Encerrar" volta para a tela inicial.
5. Por cima, quando abertas: **Configurações**, **Flashcards**, **Chave** e **Conversas salvas**.

Quem decide a tela é `ContentView.swift` / `MainActivity.kt`: parado mostra a tela inicial; conectando, ouvindo ou falando mostra a conversa.

## Sessão de voz

As duas IAs seguem o mesmo contrato (no Android, a interface `VoiceSession`):

| | |
|---|---|
| `status` | `idle` → `connecting` → `listening` ⇄ `speaking` |
| `lines` | as falas da conversa (você e o tutor), em texto |
| `audioLevel` | volume da voz do tutor, para a boca do avatar |
| `error` | mensagem em português quando algo dá errado |
| `start` / `stop` | abre e fecha a sessão |
| `pressToTalk` / `releaseToTalk` | segure para falar |

**Segure para falar:** a detecção automática de fala fica desligada. O áudio do microfone só vai para a IA enquanto o botão está apertado. Ao soltar, o app espera 300 ms (para não cortar a última sílaba) e pede a resposta. Se você soltar sem ter falado, nada é enviado.

**O tutor começa falando:** logo depois de conectar, o app pede a primeira resposta.

### Gemini Live

`GeminiLive.swift` / `GeminiLive.kt`. WebSocket com a Gemini Live API.

- Microfone em PCM 16 kHz; a voz dela chega em PCM 24 kHz.
- `contextWindowCompression` ligado: sem isso a conversa por voz acaba em ~15 minutos.
- Quando o Google avisa que vai derrubar a conexão (`goAway`), o app reconecta na mesma sessão (`sessionResumption`) sem você perceber.
- Vozes: Kore (Ana) e Puck (Peter).

### OpenAI Realtime

`Realtime.swift` (iOS, por WebRTC) / `OpenAIRealtime.kt` (Android, por WebSocket). Mesma API e mesmos eventos nos dois.

- No iOS o app cria um token temporário (`/v1/realtime/client_secrets`) e conecta por WebRTC.
- No Android conecta direto por WebSocket, com áudio PCM 24 kHz.
- A transcrição da sua fala usa um prompt que pede o texto **com os erros**, para as correções fazerem sentido.
- Vozes: marin (Ana) e cedar (Peter).

## Instruções do tutor

`Prompt.swift` / `Prompt.kt` montam as instruções com o nome do tutor, o nome e os interesses da pessoa e, ao continuar uma conversa salva, o histórico.

O tutor responde em inglês e, quando você erra, inclui uma correção curta. O app separa a fala das correções (`Correction.split`, em `ConversationView.swift` / `Prompt.kt`) para mostrar o bloco **Quick fix** e traduzir cada parte separadamente.

## Chaves

`APIKeys.swift` / `ApiKeys.kt` + `KeyScreen.kt`.

- **iOS:** Keychain, serviço `mytutor.apikeys`, acessível só neste aparelho depois do primeiro desbloqueio.
- **Android:** criptografada com AES-GCM por uma chave do Android Keystore; o backup do app fica desligado.
- Antes de salvar, o app testa a chave listando os modelos da IA (`200` aceita, `429` aceita com aviso de limite, o resto recusa).
- A tela de chave explica que a chave é só da pessoa e mostra o passo a passo com links.

## Tradução e flashcards

- **Tradução:** no próprio aparelho, com o Translation da Apple no iOS e o ML Kit no Android (baixa o idioma na primeira vez).
- **Flashcards:** palavra ou expressão, tradução e a frase da conversa como exemplo. A revisão volta em 1, 3, 7, 15 e 30 dias.

## Dados salvos

| O quê | iOS | Android |
|---|---|---|
| Perfil, preferências | `UserDefaults` / `@AppStorage` | `SharedPreferences` (`mytutor`) |
| Conversas salvas | JSON em `UserDefaults` | JSON em `SharedPreferences` |
| Flashcards | JSON em `UserDefaults` | JSON em `SharedPreferences` |
| Chaves | Keychain | Keystore + `SharedPreferences` (`api_keys`) |

## Modo grátis (oculto)

`Economy.swift` (só iOS) é um modo sem voz ao vivo: reconhecimento de fala do iPhone, resposta em texto do Gemini e voz do sistema. Está escondido da interface, mas o código continua no projeto.
