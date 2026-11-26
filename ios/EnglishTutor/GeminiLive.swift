import AVFoundation
import os


/// Conexão com a Gemini Live API via WebSocket.
/// Microfone: PCM 16-bit 16 kHz. Voz dela: PCM 16-bit 24 kHz.
@MainActor
final class GeminiLive: ObservableObject {
    @Published var status: VoiceStatus = .idle
    @Published var error: String?
    @Published var lines: [Line] = []
    /// Volume da voz dela (0 a 1).
    @Published var audioLevel: Double?

    private var task: URLSessionWebSocketTask? {
        didSet {
            let current = task
            socket.withLock { $0 = current }
        }
    }
    /// A mesma conexão, lida pela thread de áudio (muda quando reconecta).
    private let socket = OSAllocatedUnfairLock<URLSessionWebSocketTask?>(initialState: nil)
    /// Configuração da sessão, reenviada ao reconectar.
    private var setupConfig: [String: Any] = [:]
    /// Identificador para retomar a sessão; o Google fecha após ~2 min sem mensagens.
    private var resumeHandle: String?
    private var reconnecting = false
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let micFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true)!
    private let voiceFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 24000, channels: 1, interleaved: false)!
    /// Quando termina o áudio que já foi agendado para tocar.
    private var speakingUntil = Date.distantPast
    /// Fala que ainda está recebendo pedaços de transcrição.
    /// Fala aberta de cada um. Sua transcrição pode terminar de chegar depois que ela já começou a responder.
    private var openMine: Int?
    private var openTutor: Int?
    /// Estado do botão, lido na thread de áudio.
    private let mic = OSAllocatedUnfairLock(initialState: MicState())
    private var configObserver: NSObjectProtocol?

    init() {
        engine.attach(player)
    }

    func start(instructions: String, voice: String, history: [Line]) async {
        error = nil
        lines = history
        openMine = nil
        openTutor = nil
        status = .connecting
        guard await AVAudioApplication.requestRecordPermission() else {
            fail("Permita o microfone em Ajustes para conversar.")
            return
        }
        setupConfig = [
            "model": "models/gemini-3.8-live",
            "generationConfig": [
                "responseModalities": ["AUDIO"],
                "speechConfig": ["voiceConfig": ["prebuiltVoiceConfig": ["voiceName": voice]]],
            ],
            "systemInstruction": ["parts": [["text": instructions]]],
            // Sua fala na tela: inglês (com português quando você trava). Evita transcrever em outro idioma.
            "inputAudioTranscription": ["languageCodes": ["en-US", "pt-BR"]],
            "outputAudioTranscription": [String: Any](),
            // Aperte para falar: sem detecção automática de fala.
            "realtimeInputConfig": ["automaticActivityDetection": ["disabled": true]],
            // Sem isso, conversa por voz acaba em ~15 min ("contexto" cheio). Com a janela deslizante,
            // o Gemini esquece só o começo mais antigo e a conversa pode durar o quanto quiser.
            "contextWindowCompression": ["slidingWindow": [String: Any]()],
        ]
        resumeHandle = nil
        reconnecting = false
        connect()
    }

    /// Abre a conexão; com `resumeHandle`, retoma a mesma sessão (contexto preservado).
    private func connect() {
        let url = URL(string: "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=\(APIKeys.shared.gemini)")!
        let task = URLSession.shared.webSocketTask(with: url)
        self.task = task
        task.resume()
        receive()
        var setup = setupConfig
        setup["sessionResumption"] = resumeHandle.map { ["handle": $0] } ?? [String: Any]()
        send(["setup": setup])
    }

    // MARK: - Aperte para falar

    func pressToTalk() {
        if status == .speaking {
            player.stop()
            player.play()
            speakingUntil = .distantPast
        }
        openMine = nil // nova fala sua começa um balão novo
        // O activityStart sai junto com o primeiro pedaço de áudio (veja startAudio).
        if !engine.isRunning { restartAudioIfNeeded() }
        mic.withLock { $0 = MicState(on: true) }
    }

    func releaseToTalk() async {
        // Espera um pouco para não cortar a última sílaba.
        try? await Task.sleep(for: .milliseconds(300))
        let started = mic.withLock { state in
            defer { state = MicState() }
            return state.started
        }
        // Soltou sem falar: nada foi enviado, então não há o que encerrar (e ela não responde).
        // Sem áudio enviado, o Gemini recusa o activityEnd e fecha a conexão.
        if started { send(["realtimeInput": ["activityEnd": [String: Any]()]]) }
    }

    func stop() {
        mic.withLock { $0 = MicState() }
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        resumeHandle = nil
        reconnecting = false
        if let configObserver { NotificationCenter.default.removeObserver(configObserver) }
        configObserver = nil
        engine.inputNode.removeTap(onBus: 0)
        player.removeTap(onBus: 0)
        audioLevel = nil
        player.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        speakingUntil = .distantPast
        status = .idle
    }

    private func fail(_ message: String) {
        stop()
        error = Self.friendly(message)
    }

    /// Mensagens do Gemini (em inglês e técnicas) em português claro.
    private static func friendly(_ message: String) -> String {
        let m = message.lowercased()
        if m.contains("quota") || m.contains("resource_exhausted") || m.contains("rate limit") {
            return "A cota gratuita do Gemini acabou por agora. Espere alguns minutos e tente de novo."
        }
        if m.contains("api key") || m.contains("api_key") || m.contains("permission") || m.contains("unauthenticated") {
            return "O Gemini recusou sua chave. Confira a chave em Configurações → Chave."
        }
        if m.contains("microfone") || m.contains("áudio") { return message }
        if m.contains("context") || m.contains("token") || m.contains("too long") || m.contains("exceed") || m.contains("deadline") || m.contains("session") {
            return "A conversa ficou longa demais e o Gemini encerrou. Ela foi salva: toque no relógio para continuar de onde parou."
        }
        return "A conexão com o Gemini caiu. Sua conversa foi salva: toque no relógio para continuar."
    }

    // MARK: - WebSocket

    private func send(_ message: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: message) else { return }
        task?.send(.string(String(decoding: data, as: UTF8.self))) { _ in }
    }

    private func receive() {
        guard let current = task else { return }
        current.receive { [weak self] result in
            Task { @MainActor in self?.handle(result, from: current) }
        }
    }

    private func handle(_ result: Result<URLSessionWebSocketTask.Message, Error>, from current: URLSessionWebSocketTask) {
        // Ignora mensagens de uma conexão antiga (antes de reconectar).
        guard let task, task === current, status != .idle else { return }
        let data: Data
        switch result {
        case .failure(let failure):
            // Caiu (ex.: 2 min parado)? Retoma a sessão em silêncio, sem perder a conversa.
            if resumeHandle != nil, !reconnecting {
                reconnecting = true
                connect()
                return
            }
            let reason = task.closeReason.map { String(decoding: $0, as: UTF8.self) }
            fail(reason.flatMap { $0.isEmpty ? nil : $0 } ?? failure.localizedDescription)
            return
        case .success(.data(let d)): data = d
        case .success(.string(let s)): data = Data(s.utf8)
        case .success: return
        }
        receive()

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        if let update = json["sessionResumptionUpdate"] as? [String: Any],
           let handle = update["newHandle"] as? String, !handle.isEmpty {
            resumeHandle = handle
        }

        // O Gemini avisa antes de derrubar a conexão: já reconecta na mesma sessão, sem perder a conversa.
        if json["goAway"] != nil, resumeHandle != nil, !reconnecting {
            reconnecting = true
            let old = task
            connect()
            old.cancel(with: .normalClosure, reason: nil)
            return
        }

        if json["setupComplete"] != nil, reconnecting {
            reconnecting = false // retomada: o áudio já está rodando e a conversa continua
        } else if json["setupComplete"] != nil {
            do {
                try startAudio()
            } catch {
                fail("Erro no áudio: \(error.localizedDescription)")
                return
            }
            status = .listening
            // Ela começa falando.
            send(["clientContent": ["turns": [["role": "user", "parts": [["text": "Hi!"]]]], "turnComplete": true]])
        }

        if let content = json["serverContent"] as? [String: Any] {
            if content["interrupted"] as? Bool == true {
                player.stop()
                player.play()
                speakingUntil = .distantPast
            }
            if let text = (content["inputTranscription"] as? [String: Any])?["text"] as? String {
                append(text, as: .me)
            }
            if let text = (content["outputTranscription"] as? [String: Any])?["text"] as? String {
                append(text, as: .tutor)
            }
            if content["turnComplete"] as? Bool == true {
                openTutor = nil
            }
            let parts = (content["modelTurn"] as? [String: Any])?["parts"] as? [[String: Any]] ?? []
            for part in parts {
                if let base64 = (part["inlineData"] as? [String: Any])?["data"] as? String,
                   let pcm = Data(base64Encoded: base64) {
                    play(pcm)
                }
            }
        }
    }

    /// A transcrição chega em pedaços: junta na fala aberta daquela pessoa,
    /// mesmo que a outra já tenha começado a falar depois.
    private func append(_ text: String, as role: Line.Role) {
        let open = role == .me ? openMine : openTutor
        if let i = open, i < lines.count, lines[i].role == role {
            lines[i].text += text
        } else {
            lines.append(Line(role: role, text: text))
            if role == .me { openMine = lines.count - 1 } else { openTutor = lines.count - 1 }
        }
    }

    // MARK: - Áudio

    private func startAudio() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetoothHFP])
        try session.setActive(true)
        try engine.inputNode.setVoiceProcessingEnabled(true) // cancelamento de eco
        // O iOS para o motor quando a configuração de áudio muda (ao ligar o cancelamento de eco,
        // trocar de fone…). Sem religar, o microfone fica mudo e a voz dela não toca.
        configObserver = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.restartAudioIfNeeded() }
        }
        try buildAudioGraph()
    }

    private func restartAudioIfNeeded() {
        guard status != .idle, !engine.isRunning else { return }
        do {
            try buildAudioGraph()
        } catch {
            fail("Erro no áudio: \(error.localizedDescription)")
        }
    }

    /// Liga microfone e alto-falante ao motor e dá a partida.
    private func buildAudioGraph() throws {
        let input = engine.inputNode
        input.removeTap(onBus: 0)
        player.removeTap(onBus: 0)

        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, let converter = AVAudioConverter(from: inputFormat, to: micFormat) else {
            throw Failure("Formato de microfone não suportado.")
        }
        let micFormat = micFormat
        let socket = socket
        let mic = mic
        input.installTap(onBus: 0, bufferSize: 2048, format: inputFormat) { @Sendable buffer, _ in
            guard mic.withLock({ $0.on }), let pcm = Self.convert(buffer, with: converter, to: micFormat) else { return }
            let loud = Self.rms(buffer) > MicState.voiceThreshold
            let message = #"{"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=16000","data":"\#(pcm.base64EncodedString())"}}}"#
            // Só começa a enviar quando detecta voz. Antes disso guarda um trechinho
            // (para não cortar o começo da fala); se você soltar sem falar, nada é enviado.
            let toSend: [String] = mic.withLock { state in
                guard state.on else { return [] }
                if state.started { return [message] }
                if loud {
                    state.started = true
                    defer { state.preroll = [] }
                    return [#"{"realtimeInput":{"activityStart":{}}}"#] + state.preroll + [message]
                }
                state.preroll.append(message)
                if state.preroll.count > 8 { state.preroll.removeFirst() }
                return []
            }
            let task = socket.withLock { $0 }
            for text in toSend { task?.send(.string(text)) { _ in } }
        }

        engine.connect(player, to: engine.mainMixerNode, format: voiceFormat)
        // Volume do que está tocando, para a boca.
        player.installTap(onBus: 0, bufferSize: 1024, format: voiceFormat) { @Sendable [weak self] buffer, _ in
            guard let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 else { return }
            var sum: Float = 0
            for i in 0..<Int(buffer.frameLength) { sum += samples[i] * samples[i] }
            let rms = Double(sqrt(sum / Float(buffer.frameLength)))
            Task { @MainActor in self?.audioLevel = rms }
        }
        engine.prepare()
        try engine.start()
        player.play()
    }

    /// Volume (RMS) de um trecho do microfone.
    private nonisolated static func rms(_ buffer: AVAudioPCMBuffer) -> Float {
        guard let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 else { return 0 }
        var sum: Float = 0
        for i in 0..<Int(buffer.frameLength) { sum += samples[i] * samples[i] }
        return sqrt(sum / Float(buffer.frameLength))
    }

    private nonisolated static func convert(_ buffer: AVAudioPCMBuffer, with converter: AVAudioConverter, to format: AVAudioFormat) -> Data? {
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * format.sampleRate / buffer.format.sampleRate) + 1
        guard let output = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return nil }
        var consumed = false
        converter.convert(to: output, error: nil) { _, status in
            if consumed {
                status.pointee = .noDataNow
                return nil
            }
            consumed = true
            status.pointee = .haveData
            return buffer
        }
        guard output.frameLength > 0, let samples = output.int16ChannelData else { return nil }
        return Data(bytes: samples[0], count: Int(output.frameLength) * 2)
    }

    private func play(_ pcm: Data) {
        let count = pcm.count / 2
        guard count > 0, let buffer = AVAudioPCMBuffer(pcmFormat: voiceFormat, frameCapacity: AVAudioFrameCount(count)) else { return }
        buffer.frameLength = AVAudioFrameCount(count)
        let out = buffer.floatChannelData![0]
        pcm.withUnsafeBytes { raw in
            for i in 0..<count {
                out[i] = Float(Int16(littleEndian: raw.loadUnaligned(fromByteOffset: i * 2, as: Int16.self))) / 32768
            }
        }
        player.scheduleBuffer(buffer)
        status = .speaking
        // Os trechos tocam em fila: a fala termina depois do último, pela duração de cada um.
        speakingUntil = max(Date(), speakingUntil).addingTimeInterval(Double(count) / voiceFormat.sampleRate)
        let until = speakingUntil
        Task {
            try? await Task.sleep(for: .seconds(until.timeIntervalSinceNow + 0.15))
            if status == .speaking, Date() >= speakingUntil { status = .listening }
        }
    }
}


/// Estado do botão de falar, compartilhado com a thread de áudio.
private struct MicState: Sendable {
    /// Volume a partir do qual consideramos que é voz, e não silêncio ou ruído.
    static let voiceThreshold: Float = 0.01

    var on = false
    /// Já detectou voz e mandou o activityStart.
    var started = false
    /// Últimos trechos antes da voz, para não cortar o começo da fala.
    var preroll: [String] = []
}
