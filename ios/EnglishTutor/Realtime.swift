import AVFoundation
import WebRTC

enum VoiceStatus { case idle, connecting, listening, speaking }

/// Uma fala da conversa.
struct Line: Identifiable, Equatable, Codable {
    enum Role: String, Codable { case me, tutor }
    var id = UUID()
    let role: Role
    var text: String

    private enum CodingKeys: String, CodingKey { case role, text }
}

/// Conexão com a OpenAI Realtime API via WebRTC.
@MainActor
final class Realtime: NSObject, ObservableObject {
    @Published var status: VoiceStatus = .idle
    @Published var error: String?
    @Published var lines: [Line] = []
    /// Volume da voz dela (0 a 1), ou nil se não vier.
    @Published var audioLevel: Double?

    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory()
    }()

    private var pc: RTCPeerConnection?
    private var dc: RTCDataChannel?
    private var mic: RTCAudioTrack?
    private var responseActive = false
    /// Maior volume do seu microfone enquanto o botão está pressionado.
    private var micPeak = 0.0

    func start(instructions: String, voice: String, history: [Line]) async {
        error = nil
        lines = history
        status = .connecting
        do {
            guard await AVAudioApplication.requestRecordPermission() else {
                throw Failure("Permita o microfone em Ajustes para conversar.")
            }
            configureAudio()
            let token = try await fetchToken(instructions: instructions, voice: voice)

            let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
            guard let pc = Self.factory.peerConnection(with: RTCConfiguration(), constraints: constraints, delegate: nil) else {
                throw Failure("Não foi possível criar a conexão WebRTC.")
            }
            self.pc = pc

            let mic = Self.factory.audioTrack(with: Self.factory.audioSource(with: constraints), trackId: "mic")
            mic.isEnabled = false // só liga enquanto o botão está pressionado
            pc.add(mic, streamIds: ["mic"])
            self.mic = mic

            dc = pc.dataChannel(forLabel: "oai-events", configuration: RTCDataChannelConfiguration())
            dc?.delegate = self

            let offer = try await pc.offer(for: constraints)
            try await pc.setLocalDescription(offer)
            let answer = try await postSDP(offer.sdp, token: token)
            try await pc.setRemoteDescription(RTCSessionDescription(type: .answer, sdp: answer))
            pollAudioLevel()
        } catch {
            stop()
            self.error = (error as? Failure)?.message ?? error.localizedDescription
        }
    }

    // MARK: - Aperte para falar

    func pressToTalk() {
        send(["type": "input_audio_buffer.clear"])
        if responseActive { send(["type": "response.cancel"]) }
        if status == .speaking { send(["type": "output_audio_buffer.clear"]) }
        micPeak = 0
        mic?.isEnabled = true
    }

    func releaseToTalk() async {
        // Espera um pouco para não cortar a última sílaba.
        try? await Task.sleep(for: .milliseconds(300))
        mic?.isEnabled = false
        // Soltou sem falar: descarta o áudio (silêncio/ruído) em vez de mandar ela responder.
        guard micPeak > 0.02 else {
            send(["type": "input_audio_buffer.clear"])
            return
        }
        send(["type": "input_audio_buffer.commit"])
        send(["type": "response.create"])
    }

    private func send(_ event: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: event) else { return }
        dc?.sendData(RTCDataBuffer(data: data, isBinary: false))
    }

    /// A cada ~70 ms lê o audioLevel do áudio recebido.
    private func pollAudioLevel() {
        Task {
            while let pc {
                let report = await pc.statistics()
                let level = report.statistics.values
                    .first { $0.type == "inbound-rtp" && $0.values["kind"] as? String == "audio" }?
                    .values["audioLevel"] as? NSNumber
                audioLevel = level?.doubleValue
                if mic?.isEnabled == true,
                   let mine = report.statistics.values
                    .first(where: { $0.type == "media-source" && $0.values["kind"] as? String == "audio" })?
                    .values["audioLevel"] as? NSNumber {
                    micPeak = max(micPeak, mine.doubleValue)
                }
                try? await Task.sleep(for: .milliseconds(70))
            }
        }
    }

    func stop() {
        dc?.close()
        mic?.isEnabled = false
        pc?.close()
        dc = nil
        mic = nil
        pc = nil
        audioLevel = nil
        responseActive = false
        status = .idle
    }

    // MARK: - Áudio no alto-falante

    private func configureAudio() {
        let config = RTCAudioSessionConfiguration.webRTC()
        config.categoryOptions = [.defaultToSpeaker, .allowBluetoothHFP]
        RTCAudioSessionConfiguration.setWebRTC(config)
    }

    // MARK: - HTTP

    private func fetchToken(instructions: String, voice: String) async throws -> String {
        let body: [String: Any] = [
            "session": [
                "type": "realtime",
                "model": "gpt-realtime-2.1",
                "instructions": instructions,
                "audio": [
                    "input": [
                        "transcription": [
                            "model": "gpt-4o-mini-transcribe",
                            // O texto na tela deve mostrar o que você falou de verdade, com os erros.
                            "prompt": "Transcribe exactly what the speaker says, word for word, including grammar mistakes, wrong words, repetitions and filler words. Do not correct or improve anything. The speaker is a Brazilian learning English and may mix in Portuguese.",
                        ],
                        "turn_detection": NSNull(), // aperte para falar
                    ],
                    "output": ["voice": voice],
                ],
            ],
        ]
        var request = URLRequest(url: URL(string: "https://api.openai.com/v1/realtime/client_secrets")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(APIKeys.shared.openAI)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let data = try await send(request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let value = json["value"] as? String else {
            throw Failure("Resposta inesperada ao criar o token.")
        }
        return value
    }

    private func postSDP(_ sdp: String, token: String) async throws -> String {
        var request = URLRequest(url: URL(string: "https://api.openai.com/v1/realtime/calls")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/sdp", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(sdp.utf8)
        return String(decoding: try await send(request), as: UTF8.self)
    }

    private func send(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let message = (json?["error"] as? [String: Any])?["message"] as? String
            throw Failure(message ?? "Erro HTTP \(http.statusCode)")
        }
        return data
    }

    // MARK: - Eventos

    private func handle(type: String, transcript: String?, errorCode: String?, errorMessage: String?) {
        switch type {
        case "response.created": responseActive = true
        case "response.done": responseActive = false
        case "conversation.item.input_audio_transcription.completed":
            if let transcript, !transcript.isEmpty { lines.append(Line(role: .me, text: transcript)) }
        case "response.output_audio_transcript.done":
            if let transcript, !transcript.isEmpty { lines.append(Line(role: .tutor, text: transcript)) }
        case "output_audio_buffer.started": status = .speaking
        case "output_audio_buffer.stopped", "output_audio_buffer.cleared": status = .listening
        case "error":
            // Toque rápido demais no botão: nada para enviar, não é erro de verdade.
            if errorCode != "input_audio_buffer_commit_empty" { error = errorMessage ?? "Erro desconhecido" }
        default: break
        }
    }
}

extension Realtime: RTCDataChannelDelegate {
    nonisolated func dataChannelDidChangeState(_ dataChannel: RTCDataChannel) {
        guard dataChannel.readyState == .open else { return }
        // Ela começa falando.
        dataChannel.sendData(RTCDataBuffer(data: Data(#"{"type":"response.create"}"#.utf8), isBinary: false))
        Task { @MainActor in self.status = .listening }
    }

    nonisolated func dataChannel(_ dataChannel: RTCDataChannel, didReceiveMessageWith buffer: RTCDataBuffer) {
        guard let json = try? JSONSerialization.jsonObject(with: buffer.data) as? [String: Any],
              let type = json["type"] as? String else { return }
        let transcript = json["transcript"] as? String
        let errorInfo = json["error"] as? [String: Any]
        let errorCode = errorInfo?["code"] as? String
        let errorMessage = errorInfo?["message"] as? String
        Task { @MainActor in self.handle(type: type, transcript: transcript, errorCode: errorCode, errorMessage: errorMessage) }
    }
}

struct Failure: Error {
    let message: String
    init(_ message: String) { self.message = message }
}
