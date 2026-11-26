import Security
import SwiftUI

/// Chaves das IAs que a própria pessoa cadastra. Ficam no Keychain deste iPhone
/// (sem backup nem iCloud) e só são enviadas direto para o Google ou a OpenAI.
@Observable
final class APIKeys {
    static let shared = APIKeys()

    private(set) var gemini: String
    private(set) var openAI: String

    private init() {
        gemini = Self.read("gemini")
        openAI = Self.read("openai")
    }

    func save(_ value: String, for provider: Provider) {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        switch provider {
        case .gemini, .economy: gemini = value; Self.write(value, account: "gemini")
        case .openAI: openAI = value; Self.write(value, account: "openai")
        }
    }

    // MARK: - Keychain

    private static func query(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "mytutor.apikeys",
         kSecAttrAccount as String: account]
    }

    private static func read(_ account: String) -> String {
        var q = query(account)
        q[kSecReturnData as String] = true
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    private static func write(_ value: String, account: String) {
        SecItemDelete(query(account) as CFDictionary)
        guard !value.isEmpty else { return }
        var q = query(account)
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }

    // MARK: - Testar a chave

    /// Faz uma chamada simples (listar modelos, não gasta nada) para ver se a chave funciona.
    static func check(_ key: String, for provider: Provider) async -> String? {
        var request: URLRequest
        switch provider {
        case .gemini, .economy:
            request = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models")!)
            request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
        case .openAI:
            request = URLRequest(url: URL(string: "https://api.openai.com/v1/models")!)
            request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        }
        request.timeoutInterval = 15
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            if code == 200 { return nil }
            if code == 429 { return "Essa chave atingiu o limite agora. Ela parece válida; tente mais tarde." }
            return "Essa chave não funcionou. Confira se copiou ela inteira."
        } catch {
            return "Não deu para testar a chave. Você está sem internet?"
        }
    }
}

/// Cadastro da chave: aviso de privacidade, passo a passo com links e o campo para colar.
struct APIKeyView: View {
    var provider: Provider
    /// Chamado ao salvar (true) ou apagar (false) a chave.
    var onDone: (_ saved: Bool) -> Void

    @State private var key = ""
    @State private var checking = false
    @State private var problem: String?
    @FocusState private var typing: Bool

    private var keys = APIKeys.shared

    init(provider: Provider, onDone: @escaping (_ saved: Bool) -> Void) {
        self.provider = provider
        self.onDone = onDone
    }

    private var current: String { provider == .openAI ? keys.openAI : keys.gemini }
    private var cleanKey: String { key.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Chave escondida: só o começo e o fim aparecem ("AIza••••••1234").
    private var maskedKey: String {
        let k = cleanKey
        guard k.count > 8 else { return String(repeating: "•", count: k.count) }
        return k.prefix(4) + String(repeating: "•", count: 8) + k.suffix(4)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            privacy
            tutorial
            field
            if !current.isEmpty {
                Button("Apagar a chave salva", systemImage: "trash", role: .destructive) {
                    keys.save("", for: provider)
                    onDone(false)
                }
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.danger)
                .frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(.white)
    }

    private var privacy: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 22))
                .foregroundStyle(Theme.yellow)
            VStack(alignment: .leading, spacing: 4) {
                Text("Sua chave é só sua").font(.system(size: 15, weight: .heavy))
                Text("O app é grátis e não tem servidor: ele fala direto com \(provider == .openAI ? "a OpenAI" : "o Google") usando a sua chave. Ela fica guardada só neste iPhone, protegida no Keychain. Nós nunca vemos nem guardamos sua chave, e qualquer custo de uso aparece na sua conta \(provider == .openAI ? "da OpenAI" : "do Google").")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.muted)
            }
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
    }

    private var tutorial: some View {
        VStack(alignment: .leading, spacing: 12) {
            Overline(text: "Como pegar sua chave")
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .top, spacing: 12) {
                    Text("\(index + 1)")
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundStyle(Theme.background)
                        .frame(width: 24, height: 24)
                        .background(Theme.yellow, in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(step.text)
                            .font(.system(size: 14, weight: .medium))
                            .fixedSize(horizontal: false, vertical: true)
                        if let link = step.link {
                            Link(destination: URL(string: link.url)!) {
                                Label(link.title, systemImage: "arrow.up.right.square")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(Theme.yellow)
                            }
                        }
                    }
                }
            }
            Text(provider == .openAI
                 ? "A OpenAI cobra pelo uso (a partir de US$ 5 de crédito). Uma conversa de 10 minutos costuma custar centavos de dólar."
                 : "O Gemini tem uso grátis com limite diário. Não precisa de cartão.")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
        }
    }

    private struct Step {
        var text: String
        var link: (title: String, url: String)?
    }

    private var steps: [Step] {
        switch provider {
        case .gemini, .economy:
            [Step(text: "Abra o Google AI Studio e entre com sua conta Google.",
                  link: ("Abrir o Google AI Studio", "https://aistudio.google.com/apikey")),
             Step(text: "Toque em \"Create API key\" (Criar chave de API). Se pedir, aceite os termos e escolha ou crie um projeto."),
             Step(text: "Copie a chave que aparecer (começa com \"AIza\"), volte aqui e cole abaixo.")]
        case .openAI:
            [Step(text: "Crie sua conta (ou entre) na plataforma da OpenAI. É diferente da assinatura do ChatGPT.",
                  link: ("Abrir a plataforma da OpenAI", "https://platform.openai.com/signup")),
             Step(text: "Adicione crédito na sua conta. Sem crédito a voz não funciona.",
                  link: ("Abrir a página de pagamento", "https://platform.openai.com/settings/organization/billing/overview")),
             Step(text: "Crie uma chave em \"Create new secret key\" e copie (começa com \"sk-\"). Ela só aparece uma vez.",
                  link: ("Abrir a página de chaves", "https://platform.openai.com/api-keys")),
             Step(text: "Volte aqui e cole a chave abaixo.")]
        }
    }

    private var field: some View {
        VStack(alignment: .leading, spacing: 8) {
            Overline(text: current.isEmpty ? "Sua chave" : "Trocar a chave (já tem uma salva)")
            HStack(spacing: 8) {
                // Campo comum (não de senha) para o iPhone não oferecer "salvar senha"; Enter já salva.
                // Fora da digitação, a chave aparece escondida.
                ZStack(alignment: .leading) {
                    TextField("", text: $key, prompt: Text("Cole a chave aqui").foregroundStyle(Color(hex: 0x7D8A8E)))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .focused($typing)
                        .onSubmit { Task { await save() } }
                        .opacity(typing || key.isEmpty ? 1 : 0)
                    if !typing, !key.isEmpty {
                        Text(maskedKey)
                            .lineLimit(1)
                            .contentShape(Rectangle())
                            .onTapGesture { typing = true }
                            .accessibilityLabel("Chave colada, escondida")
                    }
                }
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                if key.isEmpty {
                    // Botão de colar do sistema: não aparece o aviso "Permitir colar?".
                    PasteButton(payloadType: String.self) { strings in
                        Task { @MainActor in
                            key = strings.first ?? ""
                            problem = nil
                            typing = false
                        }
                    }
                    .labelStyle(.titleOnly)
                    .buttonBorderShape(.capsule)
                    .tint(Theme.yellow)
                    .controlSize(.small)
                } else {
                    Button { key = ""; problem = nil } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.muted)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Limpar chave")
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 52)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border, lineWidth: 2))

            if let problem {
                Text(problem)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.danger)
            }

            Button {
                Task { await save() }
            } label: {
                HStack(spacing: 8) {
                    if checking { ProgressView().tint(Theme.background) }
                    Text(checking ? "Testando a chave…" : "Salvar chave")
                }
                .font(.system(size: 16, weight: .heavy))
                .foregroundStyle(cleanKey.isEmpty ? Theme.muted : Theme.background)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(cleanKey.isEmpty ? Theme.border : Theme.yellow, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(cleanKey.isEmpty || checking)
        }
    }

    private func save() async {
        guard !cleanKey.isEmpty, !checking else { return }
        typing = false
        checking = true
        problem = await APIKeys.check(cleanKey, for: provider)
        checking = false
        guard problem == nil else { return }
        keys.save(cleanKey, for: provider)
        key = ""
        onDone(true)
    }
}

/// A tela de chave aberta como folha (Configurações e tela inicial).
struct APIKeySheet: View {
    var provider: Provider
    var onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                APIKeyView(provider: provider) { saved in
                    if saved { onSaved() }
                    dismiss()
                }
                .padding(20)
            }
            .background(Theme.background)
            .navigationTitle("Chave do \(provider == .openAI ? "OpenAI" : "Gemini")")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
