import SwiftUI

/// Lista das conversas salvas: tocar continua, deslizar (ou o botão de lixeira) apaga.
struct HistoryView: View {
    var conversations: [SavedConversation]
    var onContinue: (SavedConversation) -> Void
    var onDelete: (SavedConversation) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pendingDelete: SavedConversation?

    var body: some View {
        NavigationStack {
            Group {
                if conversations.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 32))
                            .foregroundStyle(Theme.muted)
                        Text("Nenhuma conversa salva")
                            .font(.system(size: 15, weight: .semibold))
                        Text("Durante uma conversa, toque no marcador para salvar.")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(32)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(conversations) { conversation in
                            row(conversation)
                                .listRowBackground(Theme.card)
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) { onDelete(conversation) } label: {
                                        Label("Apagar", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Theme.background)
            .navigationTitle("Conversas salvas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
            .confirmationDialog("Apagar esta conversa?", isPresented: deleteDialogShown, titleVisibility: .visible, presenting: pendingDelete) { conversation in
                Button("Apagar", role: .destructive) { onDelete(conversation) }
            } message: { _ in
                Text("Não dá para desfazer.")
            }
        }
        .preferredColorScheme(.dark)
    }

    private var deleteDialogShown: Binding<Bool> {
        Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } })
    }

    private func row(_ conversation: SavedConversation) -> some View {
        HStack(spacing: 12) {
            Button {
                onContinue(conversation)
            } label: {
                HStack(spacing: 12) {
                    TutorFace(tutor: conversation.tutor, size: 44)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(conversation.title)
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(2)
                            .foregroundStyle(.white)
                        Text("\(conversation.tutor == "peter" ? "Peter" : "Ana") · \(conversation.lines.count) falas · \(conversation.savedAt.formatted(.relative(presentation: .named).locale(Locale(identifier: "pt_BR"))))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Theme.muted)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Continuar esta conversa")

            Button {
                pendingDelete = conversation
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.danger)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Apagar conversa")
        }
        .padding(.vertical, 4)
    }
}
