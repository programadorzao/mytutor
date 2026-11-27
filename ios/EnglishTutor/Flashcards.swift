import SwiftUI
import Translation

// MARK: - Cartão e repetição

/// Um flashcard: palavra ou expressão em inglês, tradução e a frase da conversa como exemplo.
struct Flashcard: Codable, Identifiable {
    var id = UUID()
    var front: String
    var back: String
    var example: String
    /// Nível de 0 a 5: sobe quando você acerta, volta a 0 quando erra.
    var level = 0
    /// Quando o cartão aparece de novo na revisão.
    var due = Date.now
}

/// Cartões guardados no próprio aparelho, com repetição simples (estilo Anki).
enum FlashcardStore {
    private static let key = "flashcards"
    /// Dias até a próxima revisão para cada nível (nível 0 = hoje mesmo).
    private static let days = [0, 1, 3, 7, 15, 30]

    static func all() -> [Flashcard] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let cards = try? JSONDecoder().decode([Flashcard].self, from: data) else { return [] }
        return cards
    }

    /// Cartões para revisar agora, dos mais atrasados para os mais novos.
    static func due() -> [Flashcard] {
        all().filter { $0.due <= .now }.sorted { $0.due < $1.due }
    }

    static func add(_ card: Flashcard) {
        write(all() + [card])
    }

    static func delete(_ id: UUID) {
        write(all().filter { $0.id != id })
    }

    /// Acertou: sobe de nível e volta daqui a alguns dias. Errou: volta ao nível 0 e reaparece hoje.
    static func review(_ card: Flashcard, correct: Bool) {
        var card = card
        card.level = correct ? min(card.level + 1, days.count - 1) : 0
        card.due = Calendar.current.date(byAdding: .day, value: days[card.level], to: .now) ?? .now
        write(all().map { $0.id == card.id ? card : $0 })
    }

    private static func write(_ cards: [Flashcard]) {
        guard let data = try? JSONEncoder().encode(cards) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}

// MARK: - Criar cartão a partir de uma fala

/// Escolha uma palavra (ou um trecho seguido, no modo Expressão), confira a tradução e adicione.
/// Dá para criar vários cartões da mesma frase, um de cada vez.
struct NewCardView: View {
    var sentence: String
    var onSaved: () -> Void

    private enum Mode: String, CaseIterable { case word = "Palavra", phrase = "Expressão" }

    @Environment(\.dismiss) private var dismiss
    @State private var mode = Mode.word
    /// Palavras marcadas (posição na frase). No modo Palavra, no máximo uma.
    @State private var selected: Set<Int> = []
    @State private var front = ""
    @State private var back = ""
    @State private var translating = false
    @State private var translationConfig: TranslationSession.Configuration?
    /// Cartões já criados nesta tela.
    @State private var added: [String] = []

    private var words: [String] {
        sentence.split(whereSeparator: \.isWhitespace).map(String.init)
    }

    private var canAdd: Bool {
        !clean(front).isEmpty && !back.trimmingCharacters(in: .whitespaces).isEmpty && !translating
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Picker("Tipo", selection: $mode) {
                        ForEach(Mode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: mode) { clearSelection() }

                    Overline(text: hint)
                    FlowLayout(spacing: 8) {
                        ForEach(Array(words.enumerated()), id: \.offset) { index, word in
                            let on = selected.contains(index)
                            Button { tap(index) } label: {
                                Text(word)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(on ? Theme.background : .white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(on ? Theme.yellow : Theme.card, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    field("Frente (inglês)", text: $front)
                    field(translating ? "Verso (traduzindo…)" : "Verso (tradução)", text: $back)

                    Button(action: add) {
                        Label("Adicionar cartão", systemImage: "plus")
                            .font(.system(size: 16, weight: .heavy))
                            .foregroundStyle(canAdd ? Theme.background : Theme.muted)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(canAdd ? Theme.yellow : Theme.border, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(!canAdd)

                    if !added.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Overline(text: added.count == 1 ? "1 cartão criado" : "\(added.count) cartões criados")
                            FlowLayout(spacing: 6) {
                                ForEach(Array(added.enumerated()), id: \.offset) { _, card in
                                    Label(card, systemImage: "checkmark")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundStyle(Theme.yellow)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Theme.card, in: Capsule())
                                }
                            }
                        }
                        .transition(.opacity)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Overline(text: "Exemplo")
                        Text(sentence)
                            .font(.system(size: 14, weight: .medium).italic())
                            .foregroundStyle(Theme.muted)
                    }
                }
                .padding(20)
                .animation(.easeOut(duration: 0.2), value: added.count)
            }
            .background(Theme.background)
            .navigationTitle("Novo flashcard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(added.isEmpty ? "Cancelar" : "Concluir") { dismiss() }
                }
            }
            // A sessão de tradução só vale dentro deste bloco: usar num Task solto derruba o app.
            .translationTask(translationConfig) { session in
                let text = clean(front)
                guard !text.isEmpty else { return }
                translating = true
                defer { translating = false }
                if let response = try? await session.translate(text), clean(front) == text {
                    back = response.targetText
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var hint: String {
        switch mode {
        case .word: "Toque numa palavra"
        case .phrase: "Toque nas palavras da expressão (ou em todas, para a frase inteira)"
        }
    }

    private func field(_ title: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Overline(text: title)
            TextField("", text: text, axis: .vertical)
                .font(.system(size: 17, weight: .semibold))
                .padding(12)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
        }
    }

    /// Palavra: marca só a tocada. Expressão: cada toque marca ou desmarca aquela palavra,
    /// e o cartão junta as marcadas na ordem da frase.
    private func tap(_ index: Int) {
        switch mode {
        case .word:
            selected = selected == [index] ? [] : [index]
        case .phrase:
            if selected.contains(index) { selected.remove(index) } else { selected.insert(index) }
        }
        front = selected.sorted().map { words[$0] }.joined(separator: " ")
        translate()
    }

    private func clearSelection() {
        selected = []
        front = ""
        back = ""
    }

    private func add() {
        let card = clean(front)
        FlashcardStore.add(Flashcard(front: card, back: back.trimmingCharacters(in: .whitespaces), example: sentence))
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        added.append(card)
        onSaved()
        clearSelection()
    }

    private func translate() {
        guard !clean(front).isEmpty else { back = ""; return }
        if translationConfig == nil {
            translationConfig = TranslationSession.Configuration(
                source: Locale.Language(identifier: "en"),
                target: Locale.Language(identifier: "pt-BR"))
        } else {
            translationConfig?.invalidate()
        }
    }

    /// Tira pontuação das pontas ("today?" → "today").
    private func clean(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
    }
}

// MARK: - Revisão

/// Mostra os cartões do dia: tente lembrar, vire e diga se acertou.
struct FlashcardsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var queue = FlashcardStore.due()
    @State private var flipped = false
    @State private var total = FlashcardStore.all().count

    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .bold))
                        .frame(width: 44, height: 44)
                        .background(Theme.card, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Fechar")
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Flashcards").font(.system(size: 17, weight: .heavy))
                    Text("\(queue.count) para hoje · \(total) no total")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                }
            }

            if let card = queue.first {
                cardView(card)
                Spacer(minLength: 0)
                if flipped {
                    HStack(spacing: 12) {
                        answerButton("Errei", color: Theme.danger) { answer(card, correct: false) }
                        answerButton("Acertei", color: Theme.yellow) { answer(card, correct: true) }
                    }
                } else {
                    answerButton("Mostrar resposta", color: .white) {
                        withAnimation(.easeOut(duration: 0.2)) { flipped = true }
                    }
                }
            } else {
                Spacer()
                VStack(spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Theme.yellow)
                    Text(total == 0 ? "Nenhum flashcard ainda" : "Tudo revisado por hoje!")
                        .font(.system(size: 20, weight: .heavy))
                    Text(total == 0
                         ? "Na conversa, segure o dedo numa fala e toque em \"Criar flashcard\"."
                         : "Volte amanhã para os próximos cartões.")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Theme.muted)
                        .multilineTextAlignment(.center)
                }
                Spacer()
            }
        }
        .padding(20)
        .foregroundStyle(.white)
        .background(Theme.background)
        .preferredColorScheme(.dark)
    }

    private func cardView(_ card: Flashcard) -> some View {
        VStack(spacing: 16) {
            Text(card.front)
                .font(.system(size: 30, weight: .black))
                .multilineTextAlignment(.center)
            if flipped {
                Rectangle().fill(Theme.border).frame(height: 1)
                Text(card.back)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.yellow)
                    .multilineTextAlignment(.center)
                Text("“\(card.example)”")
                    .font(.system(size: 14, weight: .medium).italic())
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, minHeight: 260)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 20))
        .overlay(alignment: .topTrailing) {
            Button {
                FlashcardStore.delete(card.id)
                next()
                total = FlashcardStore.all().count
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Apagar cartão")
        }
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { flipped = true } }
    }

    private func answerButton(_ title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .heavy))
                .foregroundStyle(Theme.background)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(color, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func answer(_ card: Flashcard, correct: Bool) {
        FlashcardStore.review(card, correct: correct)
        next()
        // Errou: o cartão volta para o fim da fila de hoje.
        if !correct, let updated = FlashcardStore.all().first(where: { $0.id == card.id }) {
            queue.append(updated)
        }
    }

    private func next() {
        flipped = false
        if !queue.isEmpty { queue.removeFirst() }
    }
}
