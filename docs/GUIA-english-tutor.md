# Guia: app pessoal de conversação em inglês com tutora 2D

## Objetivo

Um app de iPhone, só para uso próprio, para praticar inglês falado. Você abre o app, toca em um botão e conversa por voz com uma tutora ilustrada que mexe a boca enquanto fala. Ela pergunta o tema ("preciso treinar para uma entrevista", "vou apresentar algo numa reunião"), conduz a conversa em torno disso e corrige os erros relevantes sem interromper o fluxo.

A prioridade é ser o mais simples e rápido de fazer possível.

## Escopo

**Entra:**
- Conversa por voz em tempo real, com a tutora falando primeiro.
- Avatar 2D com aparência de pessoa: boca animada pela voz e piscadas.
- Correções curtas dentro da conversa.
- Legenda com a última fala da tutora, para ler as correções.
- Salvar a conversa manualmente e continuar depois. Um único "slot" de conversa salva basta.

**Não entra:** login, backend, navegador ou web app, vários avatares, plano de estudo, histórico de várias conversas, estatísticas, publicação na App Store.

## Decisões técnicas

| Tema | Decisão | Por quê |
|---|---|---|
| Plataforma | Expo (React Native + TypeScript), app nativo iOS | Stack que já domino; roda no iPhone sem navegador |
| Voz | OpenAI Realtime API via WebRTC | Voz para voz numa conexão só (fala → resposta falada), baixa latência, interrupção natural |
| WebRTC no RN | `react-native-webrtc` + `@config-plugins/react-native-webrtc` | Exige dev build (não funciona no Expo Go) |
| Áudio no alto-falante | `react-native-incall-manager` | Sem isso o iOS pode tocar a voz no fone de ouvido do telefone |
| Avatar | SVG com `react-native-svg` | Sem assets nem vídeo; boca é só uma elipse com altura variável |
| Persistência | `@react-native-async-storage/async-storage` | Só para a conversa salva |
| Autenticação na OpenAI | Chave no `.env` do app (`EXPO_PUBLIC_OPENAI_API_KEY`) | Uso pessoal, então dispensa servidor. **Configurar limite de gasto no painel da OpenAI.** |

Observação: a assinatura do ChatGPT não dá acesso à API. É preciso criar uma chave paga em platform.openai.com.

## Arquitetura

```
App.tsx            tela única, estados e botões
src/realtime.ts    conexão com a OpenAI (token, WebRTC, eventos, volume)
src/Avatar.tsx     tutora em SVG; recebe mouth (0 a 1)
src/prompt.ts      instruções da tutora + histórico ao continuar
src/storage.ts     salvar/carregar a conversa
```

O fluxo de dados é este: o microfone sobe pelo WebRTC; a voz da tutora volta como áudio remoto; os eventos chegam pelo data channel e geram as transcrições e o estado de fala; o volume do áudio recebido vira a abertura da boca.

## Conexão com a Realtime API (GA)

Confira a documentação atual antes de começar, porque nomes de modelo e eventos mudam: developers.openai.com/api/docs/guides/realtime-webrtc

1. **Token efêmero:** `POST https://api.openai.com/v1/realtime/client_secrets` com `Authorization: Bearer <API_KEY>` e corpo:
   ```json
   {
     "session": {
       "type": "realtime",
       "model": "gpt-realtime-2.1",
       "instructions": "<prompt da tutora>",
       "audio": {
         "input": {
           "transcription": { "model": "gpt-4o-mini-transcribe" },
           "turn_detection": { "type": "semantic_vad" }
         },
         "output": { "voice": "marin" }
       }
     }
   }
   ```
   A resposta traz o token em `value`.
2. **WebRTC:** criar `RTCPeerConnection`, adicionar a trilha do microfone (`mediaDevices.getUserMedia({ audio: true })`) e criar o data channel `oai-events`.
3. **SDP:** `createOffer` → `setLocalDescription` → `POST https://api.openai.com/v1/realtime/calls` com o SDP no corpo, `Authorization: Bearer <token efêmero>` e `Content-Type: application/sdp`. A resposta em texto é o SDP de `answer`.
4. **Ela começa falando:** quando o data channel abrir, enviar `{ "type": "response.create" }`.

Eventos do data channel que interessam:

| Evento | Uso |
|---|---|
| `output_audio_buffer.started` | tutora começou a falar → animar boca |
| `output_audio_buffer.stopped` / `.cleared` | parou de falar → fechar boca |
| `conversation.item.input_audio_transcription.completed` | texto do que eu falei (campo `transcript`) |
| `response.output_audio_transcript.done` | texto do que ela falou (campo `transcript`) → legenda e histórico |
| `error` | mostrar a mensagem na tela |

Para encerrar: fechar o data channel, parar as trilhas do microfone, `pc.close()` e `InCallManager.stop()`.

## Avatar e lip sync

Não precisa de vídeo. A tutora é uma ilustração original em SVG (rosto, cabelo, ombros, olhos, sobrancelhas, nariz, bochechas) e recebe uma prop `mouth` de 0 a 1:

- Abaixo de 0,08, desenhar a boca fechada como um sorriso (path curvo).
- Acima disso, uma elipse escura com `ry ≈ 3 + mouth * 13`, mostrando dentes acima de 0,25 e língua acima de 0,45.
- Piscar sozinha a cada 2,5 a 5 segundos, por cerca de 120 ms.

Para obter o valor de `mouth`:
- A cada ~70 ms, ler `pc.getStats()` e pegar `audioLevel` do relatório `inbound-rtp` de áudio.
- A cada ~60 ms, se ela estiver falando, usar `min(1, audioLevel * 5)` como alvo. Se `audioLevel` não vier, simular com um valor aleatório entre 0,25 e 0,85. Suavizar com `atual += (alvo - atual) * 0.55`.

Se um dia quiser algo mais realista, dá para trocar só esse componente por 3 ou 4 fotos da mesma pessoa (boca fechada, meio aberta, aberta) sem mexer no resto.

## Prompt da tutora

```
You are Ana, a warm and patient English conversation partner for a native Brazilian Portuguese speaker.

How the session works:
- Start with a short greeting and ask what he wants to talk about today and why (for example a job interview, a meeting, a presentation, a trip).
- Once he tells you, adapt the conversation to that goal. If it is a situation, role-play the other person (interviewer, colleague, client) whenever that helps, and step out of the role briefly to teach.
- Keep each reply short: one to three sentences, then a question that keeps him talking. He should speak more than you.

Corrections:
- When he makes a meaningful mistake in grammar, word choice or an expression that sounds unnatural, give the better version in one short sentence, like "Quick fix: you can say ...", then continue the conversation naturally.
- At most one or two corrections per turn. Ignore tiny slips that don't matter.
- If he asks how to say something, or seems stuck, help briefly (a short Portuguese explanation is fine) and go back to English.

Speak clearly at a natural pace. Never lecture.
```

Ao continuar uma conversa salva, acrescentar ao final:

```
This continues a previous session. Greet him back, recap the topic in one sentence and pick up where you left off.
Previous transcript:
Learner: ...
Ana: ...
```

Mandar só as últimas ~40 falas, para manter o prompt curto.

## Tela

Tela única, sem navegação:
- Tutora no centro, com uma linha de estado embaixo ("Conectando…", "Ana está falando", "Sua vez, pode falar") e a legenda da última fala dela.
- Parada: botão principal "Começar conversa" e, se existir conversa salva, o link "Continuar a conversa salva".
- Em conversa: botão principal "Encerrar" e o link "Salvar para continuar depois".
- Erros aparecem em uma linha de aviso acima dos botões.

## Salvar e continuar

- Durante a conversa, acumular `{ role: 'me' | 'tutor', text }` em memória a partir dos eventos de transcrição.
- "Salvar" grava `{ savedAt, lines }` no AsyncStorage numa única chave, sobrescrevendo a anterior.
- "Continuar" carrega as falas, monta o prompt com o histórico e abre uma sessão nova.

## Ordem de construção

Cada etapa tem um teste simples antes de passar para a próxima.

1. **Projeto:** `npx create-expo-app@latest english-tutor --template blank-typescript`. Instalar as dependências e configurar `app.json` com o plugin do WebRTC, `bundleIdentifier` e `NSMicrophoneUsageDescription`. Rodar `npx expo prebuild` e `npx expo run:ios --device`. **Pronto quando:** o app em branco abre no iPhone.
2. **Voz:** implementar a conexão e um botão de começar/encerrar. **Pronto quando:** consigo conversar com ela, ouvindo pelo alto-falante.
3. **Avatar:** componente SVG com a prop `mouth`, primeiro com um slider de teste. **Pronto quando:** a boca abre e fecha de forma convincente.
4. **Lip sync:** ligar `audioLevel` e os eventos de fala à boca. **Pronto quando:** a boca acompanha a voz dela e fecha quando ela para.
5. **Tutora:** aplicar o prompt e mostrar a legenda. **Pronto quando:** ela pergunta o tema, segue o assunto e corrige erros propositais.
6. **Salvar/continuar.** **Pronto quando:** salvo, fecho o app, abro de novo, continuo, e ela retoma o assunto.
7. **Build final:** `npx expo run:ios --device --configuration Release`, para rodar sem Metro.

## Pontos de atenção

- **Versões:** a versão do `@config-plugins/react-native-webrtc` precisa bater com a do Expo SDK (há uma tabela no README do plugin). Esse é o ponto mais provável de quebrar o `prebuild`. Verificar também o suporte do `react-native-webrtc` à New Architecture na versão instalada.
- **Conta Apple gratuita:** o app instalado expira em 7 dias e precisa ser reinstalado pelo Xcode. Com a conta de desenvolvedor paga, dura mais.
- **Custo:** a cobrança é por uso de áudio. Configurar limite de gasto mensal antes de usar no dia a dia.
- **Eco:** o WebRTC já faz cancelamento de eco, mas se ela "ouvir a si mesma" no alto-falante, testar com fone.

## Prompt para o Claude Code

```
Leia GUIA-english-tutor.md e implemente o app seguindo a seção "Ordem de construção",
uma etapa por vez. Ao fim de cada etapa, pare e me diga como testar antes de seguir.
Mantenha tudo o mais simples possível e não adicione nada fora do escopo.
Antes de escrever a conexão com a OpenAI, confira a documentação atual da Realtime API
(WebRTC) e ajuste nomes de modelo, campos ou eventos se tiverem mudado.
```
