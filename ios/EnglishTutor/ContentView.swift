import SwiftUI

/// Tela inicial quando parado; tela de conversa durante a sessão.
struct ContentView: View {
    @StateObject private var openAI = Realtime()
    @StateObject private var gemini = GeminiLive()
    @StateObject private var economy = Economy()
    @AppStorage("provider") private var provider: Provider = .gemini
    @State private var mouth = 0.0
    @State private var maleVoice = false
    @State private var conversations = Storage.all()
    /// Id da conversa atual no histórico (salvar de novo atualiza a mesma entrada).
    @State private var currentID = UUID()
    @State private var showHistory = false
    @State private var holding = false
    @State private var showSplash = true
    @State private var showSettings = false
    @State private var showCards = false
    @State private var dueCards = FlashcardStore.due().count
    /// Sem chave da IA escolhida, "Começar" abre o cadastro da chave.
    @State private var keyFor: Provider?
    private var profile = Profile.shared

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if showSplash {
                SplashView().transition(.opacity)
            } else if !profile.onboarded {
                OnboardingView { }.transition(.opacity)
            } else if status == .idle {
                HomeView(
                    provider: $provider,
                    maleVoice: $maleVoice,
                    hasSaved: !conversations.isEmpty,
                    dueCards: dueCards,
                    error: error,
                    onStart: {
                        currentID = UUID()
                        Task { await start(history: []) }
                    },
                    onContinue: { showHistory = true },
                    onOpenCards: { showCards = true },
                    onOpenSettings: { showSettings = true },
                    onUseFree: { provider = .economy; gemini.error = nil }
                )
                .transition(.opacity)
            } else {
                ConversationView(
                    tutor: maleVoice ? "peter" : "ana",
                    tutorName: tutorName,
                    providerName: provider.shortName,
                    lines: lines,
                    status: status,
                    error: error,
                    mouth: mouth,
                    holding: holding,
                    isSaved: isSaved,
                    onPress: press,
                    onRelease: release,
                    onSave: {
                        Storage.save(SavedConversation(id: currentID, savedAt: .now, tutor: maleVoice ? "peter" : "ana", lines: lines))
                        conversations = Storage.all()
                    },
                    onEnd: {
                        holding = false
                        switch provider {
                        case .gemini: gemini.stop()
                        case .openAI: openAI.stop()
                        case .economy: economy.stop()
                        }
                    }
                )
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: status == .idle)
        .animation(.easeOut(duration: 0.35), value: showSplash)
        .animation(.easeOut(duration: 0.35), value: profile.onboarded)
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showHistory) {
            HistoryView(
                conversations: conversations,
                onContinue: { conversation in
                    showHistory = false
                    maleVoice = conversation.tutor == "peter"
                    currentID = conversation.id
                    Task { await start(history: conversation.lines) }
                },
                onDelete: { conversation in
                    Storage.delete(conversation.id)
                    conversations = Storage.all()
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $keyFor) { option in
            APIKeySheet(provider: option) { }
        }
        .fullScreenCover(isPresented: $showCards, onDismiss: { dueCards = FlashcardStore.due().count }) {
            FlashcardsView()
        }
        // A conversa caiu com erro: salva sozinha para dar para continuar depois.
        .onChange(of: error) { _, newError in
            guard newError != nil, !lines.isEmpty else { return }
            Storage.save(SavedConversation(id: currentID, savedAt: .now, tutor: maleVoice ? "peter" : "ana", lines: lines))
            conversations = Storage.all()
        }
        // Ao voltar de uma conversa, atualiza o número de cartões novos.
        .onChange(of: status == .idle) { dueCards = FlashcardStore.due().count }
        .fullScreenCover(isPresented: $showSettings) {
            SettingsView(
                provider: $provider,
                conversations: conversations,
                onContinue: { conversation in
                    // Continuar fecha as Configurações e já entra na conversa.
                    showSettings = false
                    maleVoice = conversation.tutor == "peter"
                    currentID = conversation.id
                    Task { await start(history: conversation.lines) }
                },
                onDelete: { conversation in
                    Storage.delete(conversation.id)
                    conversations = Storage.all()
                }
            )
        }
        .task {
            if !Provider.visible.contains(provider) { provider = .gemini } // estava no modo grátis, que foi ocultado
            try? await Task.sleep(for: .seconds(1.2))
            showSplash = false
        }
        .task { await animateMouth() }
    }

    // MARK: - Segure para falar

    private func press() {
        guard !holding, status == .listening || status == .speaking else { return }
        holding = true
        switch provider {
        case .gemini: gemini.pressToTalk()
        case .openAI: openAI.pressToTalk()
        case .economy: economy.pressToTalk()
        }
    }

    private func release() {
        guard holding else { return }
        holding = false
        Task {
            switch provider {
            case .gemini: await gemini.releaseToTalk()
            case .openAI: await openAI.releaseToTalk()
            case .economy: await economy.releaseToTalk()
            }
        }
    }

    // MARK: - Sessão

    private func start(history: [Line]) async {
        guard provider.isAvailable else { keyFor = provider; return }
        let instructions = Prompt.instructions(tutor: tutorName, history: history)
        switch provider {
        case .gemini:
            await gemini.start(instructions: instructions, voice: maleVoice ? "Puck" : "Kore", history: history)
        case .openAI:
            await openAI.start(instructions: instructions, voice: maleVoice ? "cedar" : "marin", history: history)
        case .economy:
            await economy.start(instructions: instructions, male: maleVoice, history: history)
        }
    }

    private var isSaved: Bool {
        conversations.first { $0.id == currentID }?.lines.map(\.text) == lines.map(\.text)
    }

    /// A cada ~60 ms aproxima a boca do volume da voz dela.
    private func animateMouth() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(60))
            var target = 0.0
            if status == .speaking {
                target = audioLevel.map { min(1, $0 * 5) } ?? .random(in: 0.25...0.85)
            }
            mouth += (target - mouth) * 0.55
        }
    }

    private var tutorName: String { maleVoice ? "Peter" : "Ana" }

    private var audioLevel: Double? {
        switch provider {
        case .gemini: gemini.audioLevel
        case .openAI: openAI.audioLevel
        case .economy: economy.audioLevel
        }
    }

    private var lines: [Line] {
        switch provider {
        case .gemini: gemini.lines
        case .openAI: openAI.lines
        case .economy: economy.lines
        }
    }

    private var status: VoiceStatus {
        switch provider {
        case .gemini: gemini.status
        case .openAI: openAI.status
        case .economy: economy.status
        }
    }

    private var error: String? {
        switch provider {
        case .gemini: gemini.error
        case .openAI: openAI.error
        case .economy: economy.error
        }
    }
}

/// Qual IA conversa com você.
enum Provider: String, CaseIterable {
    case gemini, openAI, economy

    /// Opções que aparecem no app. O modo grátis ficou oculto (o código continua em Economy.swift).
    static let visible: [Provider] = [.gemini, .openAI]

    var shortName: String {
        switch self {
        case .gemini: "Gemini"
        case .openAI: "OpenAI"
        case .economy: "Modo grátis"
        }
    }
}
