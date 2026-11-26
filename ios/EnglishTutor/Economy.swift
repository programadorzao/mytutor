import AVFoundation
import Speech

/// Modo grátis: ouve com o reconhecimento de fala do iPhone, pensa com um modelo
/// de texto do Gemini (limite gratuito bem maior que o da voz ao vivo) e fala com a voz do iOS.
@MainActor
final class Economy: NSObject, ObservableObject {
    @Published var status: VoiceStatus = .idle
    @Published var error: String?
    @Published var lines: [Line] = []
    /// Sem nível de áudio real: a boca mexe sozinha enquanto ela fala.
    let audioLevel: Double? = nil

    private static let model = "gemini-flash-lite-latest"

    private let engine = AVAudioEngine()
    private let synthesizer = AVSpeechSynthesizer()
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var recognition: SFSpeechRecognitionTask?
    private var heard = ""
    private var heardFinal = false

    private var instructions = ""
    private var voice: AVSpeechSynthesisVoice?
    /// Conversa no formato da API (user/model).
    private var contents: [[String: Any]] = []
    private var thinking: Task<Void, Never>?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    // MARK: - Sessão

    func start(instructions: String, male: Bool, history: [Line]) async {
        error = nil
        lines = history
        contents = []
        status = .connecting
        self.instructions = instructions + """


        Important: you receive a speech-to-text transcription of what he says, not his audio, and your reply is read aloud by a text-to-speech voice.
        - Do not give pronunciation tips (you cannot hear him). Don't treat transcription spelling or punctuation as his mistakes.
        - Write plain spoken English only: no markdown, no asterisks, no emojis, no lists.
        """
        voice = Self.bestVoice(male: male)

        guard await Self.speechAllowed() else {
            fail("Permita o reconhecimento de fala em Ajustes › mytutor para usar o modo grátis.")
            return
        }
        guard recognizer?.isAvailable == true else {
            fail("O reconhecimento de fala em inglês não está disponível neste aparelho agora.")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setActive(true)
        } catch {
            fail("Erro no áudio: \(error.localizedDescription)")
            return
        }
        // Ela começa falando.
        ask("Hi!")
    }

    func stop() {
        thinking?.cancel()
        synthesizer.stopSpeaking(at: .immediate)
        stopListening()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        status = .idle
    }

    private func fail(_ message: String) {
        stop()
        error = message
    }

    // MARK: - Segure para falar

    func pressToTalk() {
        synthesizer.stopSpeaking(at: .immediate)
        thinking?.cancel()
        heard = ""
        heardFinal = false

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.addsPunctuation = true
        if recognizer?.supportsOnDeviceRecognition == true {
            request.requiresOnDeviceRecognition = true // no aparelho: grátis e sem limite
        }
        self.request = request
        recognition = recognizer?.recognitionTask(with: request) { [weak self] result, _ in
            guard let result else { return }
            let text = result.bestTranscription.formattedString
            let final = result.isFinal
            Task { @MainActor in
                self?.heard = text
                if final { self?.heardFinal = true }
            }
        }

        let input = engine.inputNode
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
            request.append(buffer)
        }
        engine.prepare()
        try? engine.start()
        status = .listening
    }

    func releaseToTalk() async {
        guard request != nil else { return }
        try? await Task.sleep(for: .milliseconds(300)) // não corta o fim da palavra
        request?.endAudio()
        // Espera o texto final (no máximo ~2 s).
        for _ in 0..<20 where !heardFinal {
            try? await Task.sleep(for: .milliseconds(100))
        }
        stopListening()
        let text = heard.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, status != .idle else { return } // segurou sem falar: não envia
        lines.append(Line(role: .me, text: text))
        ask(text)
    }

    private func stopListening() {
        engine.inputNode.removeTap(onBus: 0)
        if engine.isRunning { engine.stop() }
        recognition?.cancel()
        recognition = nil
        request = nil
    }

    // MARK: - Resposta (texto)

    private func ask(_ text: String) {
        contents.append(["role": "user", "parts": [["text": text]]])
        thinking = Task {
            do {
                let reply = try await generate()
                guard !Task.isCancelled, status != .idle else { return }
                contents.append(["role": "model", "parts": [["text": reply]]])
                lines.append(Line(role: .tutor, text: reply))
                speak(reply)
            } catch is CancellationError {
            } catch {
                guard status != .idle else { return }
                fail((error as? Failure)?.message ?? error.localizedDescription)
            }
        }
    }

    private func generate() async throws -> String {
        var request = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(Self.model):generateContent")!)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue(APIKeys.shared.gemini, forHTTPHeaderField: "x-goog-api-key")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "system_instruction": ["parts": [["text": instructions]]],
            "contents": contents,
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if (response as? HTTPURLResponse)?.statusCode != 200 {
            let message = (json?["error"] as? [String: Any])?["message"] as? String ?? "Erro do Gemini."
            throw Failure(message.localizedCaseInsensitiveContains("quota")
                ? "A cota gratuita do Gemini acabou por agora. Espere alguns minutos e tente de novo."
                : message)
        }
        let candidate = (json?["candidates"] as? [[String: Any]])?.first
        let parts = (candidate?["content"] as? [String: Any])?["parts"] as? [[String: Any]] ?? []
        let text = parts.compactMap { $0["text"] as? String }.joined()
            .replacingOccurrences(of: "*", with: "") // a voz leria os asteriscos
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw Failure("O Gemini não respondeu. Tente de novo.") }
        return text
    }

    // MARK: - Voz

    private func speak(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        synthesizer.speak(utterance)
    }

    /// A melhor voz em inglês do aparelho (Premium > Enhanced > padrão) do gênero escolhido.
    private static func bestVoice(male: Bool) -> AVSpeechSynthesisVoice? {
        let english = AVSpeechSynthesisVoice.speechVoices().filter { $0.language == "en-US" }
        let gender: AVSpeechSynthesisVoiceGender = male ? .male : .female
        let candidates = english.filter { $0.gender == gender }
        return (candidates.isEmpty ? english : candidates)
            .max { $0.quality.rawValue < $1.quality.rawValue }
            ?? AVSpeechSynthesisVoice(language: "en-US")
    }

    private static func speechAllowed() async -> Bool {
        if SFSpeechRecognizer.authorizationStatus() == .authorized { return true }
        return await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0 == .authorized) }
        }
    }
}

extension Economy: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        Task { @MainActor in if self.status != .idle { self.status = .speaking } }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in if self.status == .speaking { self.status = .listening } }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in if self.status == .speaking { self.status = .listening } }
    }
}
