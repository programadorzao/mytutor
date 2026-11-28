# mytutor

**English** · [Português](README.pt-BR.md)

A free, open source app for practicing English **by talking out loud** with Ana or Peter, voice tutors who correct you mid-conversation without killing the flow.

Built for Brazilian learners: the interface, explanations and translations are in Portuguese; the conversation itself is in English.

- **iOS** (Swift + SwiftUI) and **Android** (Kotlin + Jetpack Compose), with the same screens on both.
- **No server, no account.** The app talks straight to the AI using each person's own API key.
- **Gemini Live** (free Google key, with a daily limit) or **OpenAI Realtime** (paid key).

## Features

- **Voice conversation:** hold the button to talk; the tutor answers out loud and their mouth moves with the voice.
- **Quick fix:** when you make a mistake, the tutor shows the right way (`I have 26 years` → `I'm 26 years old`) and keeps the conversation going.
- **Ear training:** hides the tutor's text until you tap to reveal it.
- **Translation:** long-press a message to translate it, with the message and each correction shown separately. Runs on-device (Apple Translation / ML Kit).
- **Flashcards:** create cards from words or phrases in the conversation, with spaced review (1, 3, 7, 15 and 30 days).
- **History:** save a conversation and pick it up later where you left off.
- **Profile:** name, photo and interests, which the tutor uses to start conversations.

## Your key stays yours

mytutor has no backend. Each person enters their own key inside the app, and it:

- is stored **only on the device** (Keychain on iOS, Android Keystore on Android);
- goes **straight from the phone to Google or OpenAI**, never through a server of ours;
- **never goes in the code**, a config file or a build.

The app walks users through getting a key for each service, step by step, with links.

| AI | Key | Cost |
|---|---|---|
| Gemini Live | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Free, with a daily limit |
| OpenAI Realtime | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) | Paid (needs account credit; a ChatGPT subscription doesn't count) |

## Running the project

### iOS

You need a Mac with Xcode and an iPhone or simulator running iOS 18 or later.

1. Open `ios/EnglishTutor.xcodeproj` in Xcode.
2. In **Signing & Capabilities**, pick your team and change the Bundle Identifier to one of your own (for example `com.yourname.EnglishTutor`).
3. Run with ⌘R. The onboarding asks for your name, interests and key.

WebRTC (used for OpenAI on iOS) comes through Swift Package Manager, and Xcode downloads it automatically.

### Android

You need Android Studio and a phone or emulator running Android 8.0 (API 26) or later.

1. In Android Studio, open the `android/` folder.
2. Wait for Gradle to sync, then run the app (▶).
3. The onboarding asks for your name, interests and key.

From the terminal:

```bash
cd android && ./gradlew :app:installDebug
```

> Voice conversations need a microphone. On the emulator, turn on the computer's microphone in the emulator settings.

## Project structure

| Folder | Contents |
|---|---|
| `ios/` | Native iOS app (Swift + SwiftUI). |
| `android/` | Native Android app (Kotlin + Jetpack Compose). |
| `shared/avatars/` | Original tutor photos (mouth closed, half open, open, blinking). |
| `shared/brand/` | Logo, symbol, icons and brand colors. |
| `docs/` | [Architecture](docs/ARQUITETURA.md) (in Portuguese): how each part works and where it lives in the code. |

## Contributing

Contributions are welcome! Read the [contributing guide](CONTRIBUTING.md) (in Portuguese) before opening a pull request. The most important rule: **no API keys in the code, ever.**

Issues and pull requests in English or Portuguese are both fine.

## License

[MIT](LICENSE).
