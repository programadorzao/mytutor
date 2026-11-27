# Como contribuir

Obrigado por querer ajudar no mytutor! Este guia explica as regras do projeto e como mandar sua mudança.

## Regras do projeto

Estas regras não mudam. Um pull request que quebre alguma delas não entra.

1. **Nenhuma chave de API no código.** Nem no código, nem em `local.properties`, `BuildConfig`, `.env`, `Info.plist` ou arquivo de "Secrets", nem só para testar. A chave é sempre digitada pela pessoa dentro do app e fica guardada só no aparelho (Keychain no iOS, Keystore no Android).
2. **Sem servidor.** O app fala direto com o Google ou a OpenAI. Não adicione backend, login, analytics nem nada que mande dados da pessoa para outro lugar.
3. **iOS e Android iguais.** As duas versões têm as mesmas telas, os mesmos textos e o mesmo comportamento. Se mudar uma, mude a outra no mesmo PR, ou abra uma issue dizendo o que falta na outra plataforma.
4. **Textos em português do Brasil** na interface. A conversa com o tutor é em inglês.
5. **Grátis.** Nada de anúncio, assinatura ou funcionalidade paga dentro do app.

## Passo a passo

1. Abra uma **issue** antes de começar algo grande, para combinar a ideia.
2. Faça um **fork** do repositório e crie um branch a partir da `main`, por exemplo `fix/traducao-quick-fix`.
3. Faça a mudança e **teste no aparelho ou no simulador/emulador**. Conversa por voz, tradução e flashcards precisam ser testados de verdade, com a sua própria chave.
4. Abra o **pull request** explicando:
   - o que mudou e por quê;
   - em qual plataforma testou (iOS, Android ou as duas) e como;
   - prints ou um vídeo curto, se mudou alguma tela.

## Onde fica cada coisa

O mapa completo está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md). Em resumo, cada arquivo do iOS tem um equivalente no Android:

| O quê | iOS (`ios/EnglishTutor/`) | Android (`android/app/src/main/java/com/joabsonsouza/mytutor/`) |
|---|---|---|
| Fluxo do app | `ContentView.swift` | `MainActivity.kt` |
| Onboarding | `Onboarding.swift` | `Onboarding.kt` |
| Tela inicial | `HomeView.swift` | `HomeScreen.kt` |
| Conversa | `ConversationView.swift` | `ConversationScreen.kt` |
| Configurações | `SettingsView.swift` | `SettingsScreen.kt` |
| Cadastro da chave | `APIKeys.swift` | `ApiKeys.kt` + `KeyScreen.kt` |
| Gemini Live | `GeminiLive.swift` | `GeminiLive.kt` |
| OpenAI Realtime | `Realtime.swift` | `OpenAIRealtime.kt` |
| Instruções do tutor | `Prompt.swift` | `Prompt.kt` |
| Flashcards | `Flashcards.swift` | `FlashcardsScreen.kt` |
| Perfil e dados salvos | `Profile.swift`, `Storage.swift` | `Data.kt` |
| Cores e avatar | `Theme.swift`, `Avatar.swift` | `Theme.kt`, `Ui.kt` |

## Estilo de código

- Siga o estilo do arquivo que você está mexendo.
- Comentários em português, curtos, explicando o **porquê** e não o óbvio.
- Prefira código simples a abstrações. Este é um app pequeno.
- Sem bibliotecas novas sem necessidade; se precisar de uma, explique no PR.

## Achou um problema de segurança?

Se for algo que exponha a chave ou os dados de alguém, não abra uma issue pública: use a aba **Security → Report a vulnerability** do GitHub.
