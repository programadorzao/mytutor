# mytutor

App grátis e open source para treinar inglês **conversando por voz** com a Ana ou o Peter, tutores que corrigem você no meio da conversa, sem travar o papo.

Feito para brasileiros: as explicações, os botões e as traduções são em português; a conversa é em inglês.

- **iOS** (Swift + SwiftUI) e **Android** (Kotlin + Jetpack Compose), com as mesmas telas.
- **Sem servidor e sem conta.** O app fala direto com a IA usando a chave da própria pessoa.
- **Gemini Live** (chave grátis do Google, com limite diário) ou **OpenAI Realtime** (chave paga).

## O que o app faz

- **Conversa por voz:** segure o botão para falar; o tutor responde com voz e a boca mexe junto.
- **Quick fix:** quando você erra, o tutor mostra a forma certa (`I have 26 years` → `I'm 26 years old`) e segue a conversa.
- **Treinar o ouvido:** esconde o texto do tutor até você tocar para ver.
- **Tradução:** toque longo numa fala para traduzir, separando a fala e cada correção. Roda no próprio aparelho (Translation da Apple / ML Kit).
- **Flashcards:** crie cartões a partir de palavras ou expressões da conversa, com revisão espaçada (1, 3, 7, 15 e 30 dias).
- **Histórico:** salve a conversa e continue depois de onde parou.
- **Perfil:** nome, foto e assuntos de interesse, que o tutor usa para puxar conversa.

## Sua chave é só sua

O mytutor não tem backend. Cada pessoa cadastra a própria chave dentro do app, e ela:

- fica guardada **só no aparelho** (Keychain no iOS, Android Keystore no Android);
- vai **direto do celular para o Google ou a OpenAI**, sem passar por nenhum servidor nosso;
- **nunca entra no código**, nem em arquivo de configuração, nem em build.

O próprio app explica, passo a passo e com links, como pegar a chave em cada serviço.

| IA | Chave | Custo |
|---|---|---|
| Gemini Live | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Grátis, com limite diário |
| OpenAI Realtime | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) | Pago (precisa de crédito na conta; a assinatura do ChatGPT não serve) |

## Rodar o projeto

### iOS

Precisa de um Mac com Xcode e de um iPhone ou simulador com iOS 18 ou mais novo.

1. Abra `ios/EnglishTutor.xcodeproj` no Xcode.
2. Em **Signing & Capabilities**, escolha o seu time e troque o Bundle Identifier por um seu (por exemplo `com.seunome.EnglishTutor`).
3. Rode com ⌘R. O onboarding pede o nome, os interesses e a chave.

O WebRTC (usado pela OpenAI no iOS) vem pelo Swift Package Manager e o Xcode baixa sozinho.

### Android

Precisa do Android Studio e de um celular ou emulador com Android 8.0 (API 26) ou mais novo.

1. No Android Studio, abra a pasta `android/`.
2. Espere o Gradle sincronizar e rode o app (▶).
3. O onboarding pede o nome, os interesses e a chave.

Pelo terminal:

```bash
cd android && ./gradlew :app:installDebug
```

> A conversa por voz precisa de microfone. No emulador, ligue o microfone do computador nas configurações do emulador.

## Estrutura

| Pasta | O que tem |
|---|---|
| `ios/` | App iOS nativo (Swift + SwiftUI). |
| `android/` | App Android nativo (Kotlin + Jetpack Compose). |
| `shared/avatars/` | Fotos originais dos tutores (boca fechada, entreaberta, aberta, piscando). |
| `shared/brand/` | Logo, símbolo, ícones e cores da marca. |
| `docs/` | [Arquitetura](docs/ARQUITETURA.md): como cada parte funciona e onde fica no código. |

## Contribuir

Contribuições são bem-vindas! Leia o [guia de contribuição](CONTRIBUTING.md) antes de abrir um pull request. A regra mais importante: **nenhuma chave de API no código, nunca.**

## Licença

[MIT](LICENSE).
