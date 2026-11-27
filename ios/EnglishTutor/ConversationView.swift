import SwiftUI
import Translation

/// Tela de conversa: chat por trás, tutor flutuante (arrastável) e botão de segurar para falar.
struct ConversationView: View {
    var tutor: String
    var tutorName: String
    var providerName: String
    var lines: [Line]
    var status: VoiceStatus
    var error: String?
    var mouth: Double
    var holding: Bool
    var isSaved: Bool
    var onPress: () -> Void
    var onRelease: () -> Void
    var onSave: () -> Void
    var onEnd: () -> Void

    /// Centro do tutor flutuante; nil = posição inicial (canto superior direito).
    @State private var bubbleCenter: CGPoint?
    @State private var dragStart: CGPoint?
    /// Modo escuta: desfoca as falas do tutor. Toda conversa começa com as falas visíveis.
    /// Começa como está nas Configurações; o olho liga e desliga durante a conversa.
    @State private var listeningMode = UserDefaults.standard.bool(forKey: "listenByDefault")
    /// Falas reveladas com um toque enquanto o modo escuta está ligado.
    @State private var revealed: Set<UUID> = []
    /// Traduções pedidas com o toque longo, por fala (a fala e cada correção separadas).
    @State private var translations: [UUID: Translated] = [:]
    /// Fala esperando tradução e a configuração que dispara o tradutor da Apple.
    @State private var pendingTranslation: Line?
    @State private var translationConfig: TranslationSession.Configuration?
    /// Fala de onde você está criando um flashcard.
    @State private var cardSource: Line?

    private let bubbleSize: CGFloat = 150
    private let bottomBarHeight: CGFloat = 170

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                VStack(spacing: 0) {
                    topBar
                    chat
                }
                bottomBar
                    .frame(maxHeight: .infinity, alignment: .bottom)
                floatingTutor(in: geo.size)
            }
        }
        .foregroundStyle(.white)
        .sheet(item: $cardSource) { line in
            NewCardView(sentence: line.text.trimmingCharacters(in: .whitespacesAndNewlines)) { }
                .presentationDetents([.large])
        }
    }

    // MARK: - Topo

    private var topBar: some View {
        HStack(spacing: 8) {
            Button(action: onEnd) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Encerrar e voltar ao início")
            VStack(alignment: .leading, spacing: 2) {
                Text(tutorName).font(.system(size: 15, weight: .heavy))
                Text("\(providerName) · \(lines.count) falas")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.muted)
            }
            Spacer()
            Button {
                listeningMode.toggle()
                revealed = []
            } label: {
                Image(systemName: listeningMode ? "eye.slash.fill" : "eye")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(listeningMode ? Theme.background : .white)
                    .frame(width: 44, height: 44)
                    .background(listeningMode ? Theme.yellow : Theme.card, in: Circle())
            }
            .accessibilityLabel(listeningMode ? "Mostrar falas do tutor" : "Modo escuta: desfocar falas do tutor")
            Button(action: onSave) {
                Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isSaved ? Theme.yellow : .white)
                    .frame(width: 44, height: 44)
                    .background(Theme.card, in: Circle())
            }
            .disabled(lines.isEmpty || isSaved)
            .accessibilityLabel(isSaved ? "Conversa salva" : "Salvar para continuar depois")
            Button(action: onEnd) {
                Text("Encerrar")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.danger)
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(Theme.card, in: Capsule())
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
        .background(Theme.background)
    }

    // MARK: - Chat

    private var chat: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 18) {
                    ForEach(lines) { line in
                        if line.role == .tutor {
                            tutorMessage(line, latest: line.id == lastTutorID)
                        } else {
                            myMessage(Text(line.text.trimmingCharacters(in: .whitespacesAndNewlines)))
                                .contextMenu {
                                    Button("Criar flashcard", systemImage: "rectangle.stack.badge.plus") { openCard(line) }
                                }
                        }
                    }
                    // Fim da lista: um espaço do tamanho da barra de baixo, para a última
                    // mensagem parar acima do microfone (e não escondida atrás dele).
                    Color.clear
                        .frame(height: bottomBarHeight + 40)
                        .id(endID)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
            .defaultScrollAnchor(.bottom)
            // A sessão de tradução só vale dentro deste bloco: usar num Task solto derruba o app.
            .translationTask(translationConfig) { session in
                guard let line = pendingTranslation else { return }
                pendingTranslation = nil
                // Traduz a fala e cada correção à parte, para cada tradução ficar embaixo do seu texto.
                let parts = Correction.split(line.text)
                do {
                    let main = parts.main.isEmpty ? "" : try await session.translate(parts.main).targetText
                    var fixes: [String] = []
                    for fix in parts.fixes { fixes.append(try await session.translate(fix).targetText) }
                    translations[line.id] = Translated(main: main, fixes: fixes)
                } catch {
                    translations[line.id] = Translated(main: "Não foi possível traduzir agora.", fixes: [])
                }
            }
            .onChange(of: lines) { scrollToEnd(proxy) }
            .onAppear { scrollToEnd(proxy, animated: false) }
        }
    }

    private let endID = "end"

    private func scrollToEnd(_ proxy: ScrollViewProxy, animated: Bool = true) {
        if animated {
            withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(endID, anchor: .bottom) }
        } else {
            proxy.scrollTo(endID, anchor: .bottom)
        }
    }

    private var lastTutorID: UUID? { lines.last { $0.role == .tutor }?.id }

    private func tutorMessage(_ line: Line, latest: Bool) -> some View {
        let parts = Correction.split(line.text)
        let hidden = listeningMode && !revealed.contains(line.id)
        let toggle = {
            guard listeningMode else { return }
            withAnimation(.easeOut(duration: 0.2)) {
                if revealed.contains(line.id) { revealed.remove(line.id) } else { revealed.insert(line.id) }
            }
        }
        // No modo escuta, tocar em qualquer parte da mensagem (ou no selo escuro) revela/esconde.
        return Button(action: toggle) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    TutorFace(tutor: tutor, size: 28)
                    Overline(text: tutorName)
                    if listeningMode {
                        Image(systemName: hidden ? "eye.slash" : "eye")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Theme.muted)
                    }
                }
                ZStack {
                    tutorContent(parts, latest: latest, translation: translations[line.id])
                        .blur(radius: hidden ? 7 : 0)
                        .accessibilityHidden(hidden)
                    if hidden {
                        // Selo escuro: a cápsula inteira é clicável, não só o ícone.
                        Button(action: toggle) {
                            Label("Toque para ver", systemImage: "eye")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .frame(minHeight: 44)
                                .background(.black.opacity(0.55), in: Capsule())
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Fala oculta. Toque para ver.")
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            if translations[line.id] == nil {
                Button("Traduzir", systemImage: "character.bubble") { translate(line) }
            } else {
                Button("Esconder tradução", systemImage: "eye.slash") { translations[line.id] = nil }
            }
            Button("Criar flashcard", systemImage: "rectangle.stack.badge.plus") { openCard(line) }
        }
        .animation(.easeOut(duration: 0.2), value: hidden)
        .padding(.trailing, 28)
        .id(line.id)
    }

    /// Abre a tela do flashcard. O menu do toque longo precisa terminar de fechar antes,
    /// senão o iPhone às vezes ignora a abertura (era preciso tocar várias vezes).
    private func openCard(_ line: Line) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))
            cardSource = line
        }
    }

    /// Pede ao tradutor da Apple (no próprio iPhone) a fala em português.
    private func translate(_ line: Line) {
        pendingTranslation = line
        if translationConfig == nil {
            translationConfig = TranslationSession.Configuration(
                source: Locale.Language(identifier: "en"),
                target: Locale.Language(identifier: "pt-BR"))
        } else {
            translationConfig?.invalidate()
        }
    }

    /// Balão da fala do tutor, a correção (se houver) e a tradução (se pedida).
    @ViewBuilder
    private func tutorContent(_ parts: (main: String, fixes: [String]), latest: Bool, translation: Translated?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if !parts.main.isEmpty {
                // Todas as falas com a mesma letra (antes a última vinha em maiúsculas).
                // A tradução fica dentro do balão, logo abaixo da fala.
                VStack(alignment: .leading, spacing: 8) {
                    Text(parts.main)
                    if let main = translation?.main, !main.isEmpty {
                        translationText(main)
                    }
                }
                    .font(.system(size: 15, weight: .medium))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .lineSpacing(3)
                .background(Theme.card, in: UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 18))
            }
            if !parts.fixes.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Overline(text: "Quick fix", color: Theme.yellow)
                    ForEach(Array(parts.fixes.enumerated()), id: \.offset) { index, fix in
                        Text("“\(fix)”")
                            .font(.system(size: 14, weight: .medium).italic())
                            .foregroundStyle(Color(hex: 0xDAE0E3))
                        // Tradução de cada correção logo embaixo dela.
                        if let fixes = translation?.fixes, index < fixes.count {
                            translationText(fixes[index])
                        }
                    }
                }
                .padding(.leading, 14)
                .overlay(alignment: .leading) { Rectangle().fill(Theme.yellow).frame(width: 2) }
                .padding(.leading, 10)
            }
        }
    }

    /// Texto traduzido: ícone de tradução, cinza e em itálico, para não confundir com a fala original.
    private func translationText(_ text: String) -> some View {
        Label(text, systemImage: "character.bubble")
            .font(.system(size: 13, weight: .medium).italic())
            .foregroundStyle(Theme.muted)
            .transition(.opacity)
    }

    private func myMessage(_ content: some View) -> some View {
        VStack(alignment: .trailing, spacing: 6) {
            HStack(spacing: 8) {
                Overline(text: Prompt.learnerName)
                LearnerFace(size: 28)
            }
            content
                .font(.system(size: 15, weight: .medium))
                .lineSpacing(3)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Theme.myBubble, in: UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 4))
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.leading, 48)
    }

    // MARK: - Tutor flutuante

    private func floatingTutor(in size: CGSize) -> some View {
        let r = bubbleSize / 2
        let center = bubbleCenter ?? CGPoint(x: size.width - r - 14, y: 120 + r)
        return ZStack(alignment: .bottomTrailing) {
            Avatar(tutor: tutor, mouth: mouth, size: bubbleSize - 8)
                .padding(4)
                .background(Theme.background, in: Circle())
                .overlay(Circle().stroke(Theme.yellow.opacity(status == .speaking ? 0.6 : 0), lineWidth: 3))
            if status == .speaking && !holding {
                HStack(spacing: 3) {
                    ForEach(0..<3, id: \.self) { i in
                        Capsule().fill(Theme.yellow)
                            .frame(width: 3, height: 5 + mouth * [9, 13, 7][i])
                    }
                }
                .frame(height: 28)
                .padding(.horizontal, 9)
                .background(Theme.background, in: Capsule())
                .offset(x: -4, y: -4)
            }
        }
        .shadow(color: .black.opacity(0.45), radius: 20, y: 18)
        // Só o círculo recebe toque (arrastar); o resto da tela continua livre para o chat.
        .contentShape(Circle())
        .scaleEffect(dragStart != nil ? 1.04 : 1)
        .gesture(
            DragGesture(coordinateSpace: .global)
                .onChanged { value in
                    let start = dragStart ?? center
                    if dragStart == nil { dragStart = center }
                    bubbleCenter = CGPoint(
                        x: min(max(start.x + value.translation.width, r + 8), size.width - r - 8),
                        y: min(max(start.y + value.translation.height, 60 + r), size.height - bottomBarHeight - r)
                    )
                }
                .onEnded { _ in
                    dragStart = nil
                    // Gruda no lado mais próximo, como uma bolha flutuante.
                    guard let current = bubbleCenter else { return }
                    let x = current.x < size.width / 2 ? r + 14 : size.width - r - 14
                    withAnimation(.easeOut(duration: 0.24)) { bubbleCenter = CGPoint(x: x, y: current.y) }
                }
        )
        .accessibilityLabel("\(tutorName). Arraste para mover.")
        // Por último: o gesto acima vale só para o círculo, não para a tela inteira.
        .position(center)
    }

    // MARK: - Barra de baixo

    private var bottomBar: some View {
        HStack(alignment: .bottom) {
            VStack(spacing: 6) {
                TutorFace(tutor: tutor, size: 42)
                    .padding(2)
                    .overlay(Circle().stroke(Theme.yellow, lineWidth: 2))
                Overline(text: tutorName)
            }
            .frame(width: 64)
            Spacer()
            VStack(spacing: 10) {
                if let error {
                    Text(error)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.danger)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                }
                Text(hint)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(holding ? Theme.yellow : Theme.muted)
                    .multilineTextAlignment(.center)
                micButton
            }
            Spacer()
            VStack(spacing: 6) {
                LearnerFace(size: 46)
                Overline(text: "Você")
            }
            .frame(width: 64)
        }
        .padding(.horizontal, 20)
        .padding(.top, 40)
        .padding(.bottom, 8)
        .background(
            LinearGradient(stops: [.init(color: Theme.background.opacity(0), location: 0), .init(color: Theme.background, location: 0.34)],
                           startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)
        )
    }

    private var hint: String {
        if holding { return "Ouvindo… solte para enviar" }
        switch status {
        case .idle, .connecting: return "Conectando…"
        case .speaking: return "\(tutorName) está falando · segure para interromper"
        case .listening: return "Segure para falar"
        }
    }

    private var micButton: some View {
        ZStack {
            if holding { PulseRing() }
            Circle()
                .fill(holding ? Theme.yellow : .white)
                .shadow(color: .black.opacity(holding ? 0 : 0.4), radius: 15, y: 10)
            Image(systemName: "mic.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(Theme.background)
        }
        .frame(width: 88, height: 88)
        .scaleEffect(holding ? 1.08 : 1)
        .animation(.easeOut(duration: 0.12), value: holding)
        .opacity(status == .connecting ? 0.4 : 1)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in onPress() }
                .onEnded { _ in onRelease() }
        )
        .accessibilityElement()
        .accessibilityLabel("Segure para falar")
        .accessibilityAddTraits(.isButton)
    }
}

/// Anel amarelo que pulsa atrás do microfone enquanto você fala.
private struct PulseRing: View {
    @State private var expanded = false

    var body: some View {
        Circle()
            .fill(Theme.yellow)
            .scaleEffect(expanded ? 1.55 : 1)
            .opacity(expanded ? 0 : 0.55)
            .onAppear {
                withAnimation(.easeOut(duration: 1.1).repeatForever(autoreverses: false)) { expanded = true }
            }
    }
}

/// Separa a correção ("a better way to say that is: …") do resto da fala do tutor.
/// Tradução de uma fala do tutor: a fala em si e cada correção, na mesma ordem.
struct Translated {
    var main: String
    var fixes: [String]
}

enum Correction {
    /// Separa todas as correções da fala do tutor (pode haver mais de uma).
    static func split(_ raw: String) -> (main: String, fixes: [String]) {
        var main = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        var fixes: [String] = []
        while fixes.count < 4, let (rest, fix) = splitOne(main) {
            main = rest
            fixes.append(fix)
        }
        return (main, fixes)
    }

    private static func splitOne(_ text: String) -> (main: String, fix: String)? {
        let markers = ["better to say", "quick fix", "pronunciation tip", "tip:", "better way to say", "you can say", "you could say", "you should say", "the correct way"]
        // O marcador que aparece primeiro no texto.
        guard let marker = markers.compactMap({ text.range(of: $0, options: .caseInsensitive) }).min(by: { $0.lowerBound < $1.lowerBound }) else {
            return nil
        }
        // Início da frase que contém a correção.
        let enders: Set<Character> = [".", "!", "?"]
        var start = text.startIndex
        if let i = text[..<marker.lowerBound].lastIndex(where: { enders.contains($0) }) {
            start = text.index(after: i)
        }
        // Fim: primeira pontuação depois do marcador (e depois dos dois-pontos, se houver).
        let afterMarker = text[marker.upperBound...]
        // Marcador que já termina em ":" ("Tip:"): o texto começa logo depois dele.
        let colon = text[marker].hasSuffix(":") ? text.index(before: marker.upperBound) : afterMarker.firstIndex(of: ":")
        let searchFrom = colon.map { text.index(after: $0) } ?? marker.upperBound
        var end = text[searchFrom...].firstIndex(where: { enders.contains($0) }).map { text.index(after: $0) } ?? text.endIndex
        // Explicação curta logo depois, entre parênteses, vai junto no bloco: "Yes, I have. (…)"
        if let open = text[end...].firstIndex(where: { !$0.isWhitespace }), text[open] == "(",
           let close = text[open...].firstIndex(of: ")") {
            end = text.index(after: close)
        }

        // Aspas e espaços nas pontas atrapalham (a tela já põe as aspas da correção).
        let junk = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "\"“”'‘’"))
        var fix = String(text[start..<end]).trimmingCharacters(in: junk)
        if let colon, colon < end {
            fix = String(text[text.index(after: colon)..<end]).trimmingCharacters(in: junk)
        }
        let main = (String(text[..<start]) + " " + String(text[end...]))
            .trimmingCharacters(in: junk)
        return fix.isEmpty ? nil : (main, fix)
    }
}
