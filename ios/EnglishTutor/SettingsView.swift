import SwiftUI

/// Configurações: perfil, qual IA usar e preferências da conversa.
/// Só abre da tela inicial, então a IA nunca muda no meio de uma conversa.
struct SettingsView: View {
    @Binding var provider: Provider
    var conversations: [SavedConversation]
    var onContinue: (SavedConversation) -> Void
    var onDelete: (SavedConversation) -> Void

    @Environment(\.dismiss) private var dismiss
    @AppStorage("listenByDefault") private var listenByDefault = false
    @State private var editingProfile = false
    @State private var showHistory = false
    @State private var keyFor: Provider?
    private var profile = Profile.shared

    init(provider: Binding<Provider>, conversations: [SavedConversation], onContinue: @escaping (SavedConversation) -> Void, onDelete: @escaping (SavedConversation) -> Void) {
        _provider = provider
        self.conversations = conversations
        self.onContinue = onContinue
        self.onDelete = onDelete
    }

    private var savedCount: Int { conversations.count }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                profileCard
                providerSection
                conversationSection
                footer
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .background(Theme.background.ignoresSafeArea())
        .foregroundStyle(.white)
        .preferredColorScheme(.dark)
        // Uma IA salva que ficou sem chave volta para o Gemini.
        .onAppear { if !provider.isAvailable { provider = .gemini } }
        // A lista abre por cima das Configurações; ao fechar, você continua aqui.
        .sheet(isPresented: $showHistory) {
            HistoryView(
                conversations: conversations,
                onContinue: { conversation in
                    showHistory = false
                    onContinue(conversation)
                },
                onDelete: onDelete
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $keyFor) { option in
            APIKeySheet(provider: option) { provider = option }
        }
        .fullScreenCover(isPresented: $editingProfile) {
            OnboardingView(editing: true) { editingProfile = false }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 44, height: 44)
                    .background(Theme.card, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Voltar")
            Text("Configurações")
                .font(.system(size: 26, weight: .black))
                .tracking(-0.8)
                .accessibilityAddTraits(.isHeader)
        }
    }

    // MARK: - Perfil

    private var profileCard: some View {
        HStack(spacing: 14) {
            LearnerFace(size: 56)
            VStack(alignment: .leading, spacing: 3) {
                Text(profile.name.isEmpty ? "Sem nome" : profile.name)
                    .font(.system(size: 17, weight: .heavy))
                Text(interestSummary)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
            Button("Editar") { editingProfile = true }
                .font(.system(size: 13, weight: .bold))
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .overlay(Capsule().stroke(Theme.border, lineWidth: 2))
                .buttonStyle(.plain)
                .accessibilityLabel("Editar perfil e interesses")
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
    }

    private var interestSummary: String {
        let labels = Interest.all.filter { profile.interests.contains($0.id) }.map(\.label)
        return labels.isEmpty ? "Nenhum interesse escolhido" : labels.joined(separator: " · ")
    }

    // MARK: - IA

    private var providerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Overline(text: "Inteligência artificial")
            ForEach(Provider.visible, id: \.self) { option in
                providerRow(option)
            }
            Text("Cada pessoa usa a própria chave, que fica só neste iPhone. Toque em \"Chave\" para ver como pegar a sua.")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.muted)
        }
    }

    private func providerRow(_ option: Provider) -> some View {
        let on = provider == option
        let available = option.isAvailable
        return Button {
            // Sem chave, abre o cadastro; depois de salvar, já fica escolhida.
            if available { provider = option } else { keyFor = option }
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(option.title).font(.system(size: 15, weight: .heavy))
                        Text(available ? option.tag : "Sem chave")
                            .font(.system(size: 10, weight: .heavy))
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .foregroundStyle(option == .gemini && available ? Theme.background : Theme.muted)
                            .background(option == .gemini && available ? Theme.yellow : Theme.border, in: Capsule())
                    }
                    Text(option.detail)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Theme.muted)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Button { keyFor = option } label: {
                    Label("Chave", systemImage: "key.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(available ? .white : Theme.background)
                        .padding(.horizontal, 10)
                        .frame(minHeight: 32)
                        .background(available ? Theme.border : Theme.yellow, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(available ? "Trocar a chave do \(option.shortName)" : "Cadastrar a chave do \(option.shortName)")
                Circle()
                    .strokeBorder(on ? Theme.yellow : Color(hex: 0x5A5A55), lineWidth: on ? 7 : 2)
                    .frame(width: 22, height: 22)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(on ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.yellow : Theme.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityHint(available ? "" : "Abre o cadastro da chave")
    }

    // MARK: - Conversa

    private var conversationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Overline(text: "Conversa")
            VStack(spacing: 0) {
                Button { listenByDefault.toggle() } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Treinar o ouvido").font(.system(size: 14, weight: .bold))
                            Text("O texto do tutor fica escondido. Toque para ver.")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer(minLength: 0)
                        // Chave no estilo do app: amarela quando ligada.
                        Capsule()
                            .fill(listenByDefault ? Theme.yellow : Theme.border)
                            .frame(width: 50, height: 30)
                            .overlay(alignment: listenByDefault ? .trailing : .leading) {
                                Circle()
                                    .fill(listenByDefault ? Theme.background : .white)
                                    .frame(width: 24, height: 24)
                                    .padding(3)
                            }
                            .animation(.easeOut(duration: 0.15), value: listenByDefault)
                    }
                    .padding(.horizontal, 14)
                    .frame(minHeight: 64)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isToggle)
                .accessibilityValue(listenByDefault ? "Ligado" : "Desligado")

                Rectangle().fill(Theme.border).frame(height: 1).padding(.horizontal, 14)

                Button { showHistory = true } label: {
                    HStack(spacing: 12) {
                        Text("Conversas salvas").font(.system(size: 14, weight: .bold))
                        Spacer()
                        Text("\(savedCount)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.muted)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.muted)
                    }
                    .padding(.horizontal, 14)
                    .frame(minHeight: 52)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(savedCount == 0)
                .opacity(savedCount == 0 ? 0.45 : 1)
            }
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        }
    }

    private var footer: some View {
        VStack(spacing: 6) {
            Image("Logo").resizable().scaledToFit().frame(height: 32).opacity(0.6)
            Text("Versão \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(hex: 0x7D8A8E))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
        .accessibilityElement(children: .combine)
    }
}

extension Provider: Identifiable {
    var id: String { rawValue }

    var title: String {
        switch self {
        case .gemini: "Gemini Live"
        case .openAI: "OpenAI Realtime"
        case .economy: "Modo grátis"
        }
    }

    var tag: String {
        switch self {
        case .gemini: "Recomendado"
        case .openAI: "Pago"
        case .economy: "Sem limite"
        }
    }

    var detail: String {
        switch self {
        case .gemini: "Voz natural e corrige pronúncia. Chave grátis do Google, com limite diário."
        case .openAI: "Voz natural e corrige pronúncia. Chave paga da OpenAI (precisa de crédito)."
        case .economy: "Ouve e fala no iPhone. Mais lento e não corrige pronúncia."
        }
    }

    /// Só dá para usar quem tem chave (o modo grátis usa a do Gemini para o texto).
    var isAvailable: Bool {
        switch self {
        case .gemini, .economy: !APIKeys.shared.gemini.isEmpty
        case .openAI: !APIKeys.shared.openAI.isEmpty
        }
    }
}
