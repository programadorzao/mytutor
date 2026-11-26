# mytutor

App grátis para treinar inglês conversando por voz com a Ana ou o Peter, que corrigem você no meio da conversa.
Cada pessoa usa a própria chave do Gemini (grátis) ou da OpenAI, guardada só no aparelho.

## Estrutura

| Pasta | O que tem |
|---|---|
| `ios/` | App iOS nativo (Swift + SwiftUI). Abra `ios/EnglishTutor.xcodeproj` no Xcode. |
| `android/` | App Android nativo (Kotlin + Jetpack Compose), em construção. |
| `shared/avatars/` | Fotos originais dos tutores (fechada, meio aberta, aberta, piscando). |
| `shared/brand/` | Logo, símbolo e ícones. |
| `docs/` | Guia do projeto e decisões. |

## Rodar o iOS

1. Abra `ios/EnglishTutor.xcodeproj` no Xcode.
2. Escolha seu time em Signing & Capabilities.
3. ⌘R. No primeiro uso, o app pede sua chave do Gemini ou da OpenAI.

Nenhuma chave fica no código.
