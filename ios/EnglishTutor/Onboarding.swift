import PhotosUI
import SwiftUI

/// Abertura com o logo, mostrada por um instante ao abrir o app.
struct SplashView: View {
    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            Image("Symbol")
                .resizable()
                .frame(width: 160, height: 160)
                .overlay(alignment: .bottom) {
                    (Text("my").foregroundStyle(Theme.yellow) + Text("tutor").foregroundStyle(.white))
                        .font(.system(size: 48, weight: .heavy))
                        .tracking(-1)
                        .fixedSize()
                        .offset(y: 60)
                }
            VStack(spacing: 14) {
                Spacer()
                HStack(spacing: 8) {
                    ForEach([1.0, 0.6, 0.3], id: \.self) { opacity in
                        Circle().fill(Theme.yellow.opacity(opacity)).frame(width: 8, height: 8)
                    }
                }
                Text("Fale inglês sem travar.")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.muted)
            }
            .padding(.bottom, 24)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("mytutor")
    }
}

/// Boas-vindas → nome e foto → interesses. Ao editar pela engrenagem, começa no nome.
struct OnboardingView: View {
    var editing = false
    var onFinish: () -> Void

    private enum Step { case welcome, profile, interests, key }

    @State private var step: Step
    @State private var name: String
    @State private var photo: UIImage?
    @State private var interests: [String]
    @State private var photoItem: PhotosPickerItem?
    /// Qual IA a pessoa vai conectar no último passo.
    @AppStorage("provider") private var provider: Provider = .gemini
    @FocusState private var nameFocused: Bool

    init(editing: Bool = false, onFinish: @escaping () -> Void) {
        self.editing = editing
        self.onFinish = onFinish
        let profile = Profile.shared
        _step = State(initialValue: editing ? .profile : .welcome)
        _name = State(initialValue: profile.name)
        _photo = State(initialValue: profile.photo)
        _interests = State(initialValue: profile.interests)
    }

    /// Editando o perfil não aparece o passo da chave (ela fica nas Configurações).
    private var totalSteps: Int { editing ? 2 : 3 }

    private var cleanName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            switch step {
            case .welcome: welcome.transition(.opacity)
            case .profile:
                // O teclado cobre a parte de baixo em vez de espremer a tela; "seguinte" no teclado avança.
                profileStep
                    .ignoresSafeArea(.keyboard, edges: .bottom)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            case .interests: interestsStep.transition(.move(edge: .trailing).combined(with: .opacity))
            case .key: keyStep.transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: step)
        .foregroundStyle(.white)
        .preferredColorScheme(.dark)
        .onChange(of: photoItem) { _, item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    photo = image.squareThumbnail()
                }
            }
        }
    }

    // MARK: - Boas-vindas

    private var welcome: some View {
        VStack(spacing: 20) {
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(height: 44)
                .accessibilityLabel("mytutor")
            ZStack(alignment: .bottom) {
                Circle().stroke(Theme.yellow.opacity(0.22), lineWidth: 1).frame(width: 340, height: 340).offset(y: 30)
                Circle().fill(Theme.yellow).frame(width: 290, height: 290)
                // Os dois inteiros e um pouco sobrepostos no meio, em vez de cortados ao meio.
                ZStack(alignment: .top) {
                    tutorFigure("ana").offset(x: -62)
                    tutorFigure("peter").offset(x: 62)
                }
                .frame(width: 290, height: 330, alignment: .top)
                .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 145, bottomTrailingRadius: 145))
            }
            .frame(height: 340)
            .overlay(alignment: .topLeading) { bubble("Hi there!", angle: -4).offset(x: -6, y: -30) }
            .overlay(alignment: .topTrailing) { bubble("Let's talk!", angle: 4).offset(x: 6, y: -18) }
            .padding(.top, 12)

            VStack(spacing: 0) {
                Text("Fale inglês")
                Text("sem travar.").foregroundStyle(Theme.yellow)
            }
            .font(.system(size: 40, weight: .black))
            .tracking(-1.3)
            .padding(.top, 12)

            Text("Converse por voz com a Ana ou o Peter. Eles corrigem você no meio da conversa, sem parar o papo.")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 300)

            Spacer(minLength: 0)
            primaryButton("Vamos começar") { step = .profile }
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private func tutorFigure(_ tutor: String) -> some View {
        Image("\(tutor)_closed")
            .resizable()
            .frame(width: 230, height: 345)
            .accessibilityHidden(true)
    }

    private func bubble(_ text: String, angle: Double) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(Theme.background)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.white, in: RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.35), radius: 14, y: 12)
            .rotationEffect(.degrees(angle))
    }

    // MARK: - Nome e foto

    private var profileStep: some View {
        VStack(alignment: .leading, spacing: 22) {
            header(stepNumber: 1) {
                if editing { onFinish() } else { step = .welcome }
            }
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Como o Tutor deve")
                    Text("te chamar?").foregroundStyle(Theme.yellow)
                }
                .font(.system(size: 30, weight: .black))
                .tracking(-0.9)
                Text("Ele usa seu nome para cumprimentar e puxar conversa.")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Theme.muted)
            }

            VStack(spacing: 10) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    ZStack(alignment: .bottomTrailing) {
                        Group {
                            if let photo {
                                Image(uiImage: photo).resizable().scaledToFill()
                            } else {
                                Image(systemName: "camera")
                                    .font(.system(size: 30))
                                    .foregroundStyle(Theme.muted)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                                    .background(Theme.card)
                                    .overlay(Circle().strokeBorder(Color(hex: 0x5A5A55), style: StrokeStyle(lineWidth: 2, dash: [6, 5])))
                            }
                        }
                        .frame(width: 112, height: 112)
                        .clipShape(Circle())
                        Image(systemName: "plus")
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundStyle(Theme.background)
                            .frame(width: 34, height: 34)
                            .background(Theme.yellow, in: Circle())
                            .overlay(Circle().stroke(Theme.background, lineWidth: 3))
                    }
                }
                .accessibilityLabel(photo == nil ? "Adicionar foto" : "Trocar foto")
                HStack(spacing: 12) {
                    Text(photo == nil ? "Foto opcional · aparece nas suas falas" : "Toque para trocar a foto")
                    if photo != nil {
                        Button("Remover") { photo = nil; photoItem = nil }
                            .foregroundStyle(Theme.danger)
                    }
                }
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .leading, spacing: 8) {
                Overline(text: "Seu nome")
                TextField("", text: $name, prompt: Text("Ex.: Joabson").foregroundStyle(Color(hex: 0x7D8A8E)))
                    .font(.system(size: 18, weight: .bold))
                    .textContentType(.givenName)
                    .submitLabel(.done)
                    .focused($nameFocused)
                    .onSubmit { nameFocused = false }
                    .padding(.horizontal, 18)
                    .frame(height: 56)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(nameFocused ? Theme.yellow : Theme.border, lineWidth: 2))
                    .contentShape(Rectangle())
                    .onTapGesture { nameFocused = true }
                    .accessibilityLabel("Seu nome")
            }

            chatPreview

            Spacer(minLength: 0)
            if cleanName.isEmpty {
                disabledButton("Digite seu nome")
            } else {
                primaryButton("Continuar") { step = .interests }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
        .background {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { nameFocused = false }
        }
    }

    private var chatPreview: some View {
        VStack(alignment: .leading, spacing: 10) {
            Overline(text: "Assim vai aparecer no chat")
            HStack(alignment: .bottom, spacing: 8) {
                TutorFace(tutor: "ana", size: 30)
                Text("Hi \(cleanName.isEmpty ? "…" : cleanName)! Nice to meet you.")
                    .padding(.horizontal, 12).padding(.vertical, 9)
                    .background(Theme.border, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
            }
            HStack(alignment: .bottom, spacing: 8) {
                Spacer(minLength: 0)
                Text("Nice to meet you too!")
                    .padding(.horizontal, 12).padding(.vertical, 9)
                    .background(Theme.myBubble, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 14, bottomTrailingRadius: 4, topTrailingRadius: 14))
                LearnerFace(size: 30, name: cleanName, photo: photo)
            }
        }
        .font(.system(size: 13, weight: .semibold))
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Interesses

    private var interestsStep: some View {
        VStack(alignment: .leading, spacing: 18) {
            header(stepNumber: 2) { step = .profile }
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Sobre o que você")
                    Text("gosta de falar?").foregroundStyle(Theme.yellow)
                }
                .font(.system(size: 30, weight: .black))
                .tracking(-0.9)
                Text("Escolha pelo menos \(Profile.minInterests). O Tutor usa isso para sugerir assuntos.")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Theme.muted)
            }

            ScrollView {
                FlowLayout(spacing: 8) {
                    ForEach(Interest.all) { interest in
                        chip(interest)
                    }
                }
                .padding(2) // a borda dos chips não fica cortada pela rolagem
            }
            .scrollBounceBehavior(.basedOnSize)

            VStack(spacing: 8) {
                let missing = Profile.minInterests - interests.count
                if missing > 0 {
                    disabledButton(missing == 1 ? "Escolha mais 1" : "Escolha mais \(missing)")
                } else {
                    if editing {
                        primaryButton("Salvar") { finish() }
                    } else {
                        primaryButton("Continuar") { step = .key }
                    }
                }
                Text("Dá para mudar depois na engrenagem.")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private func chip(_ interest: Interest) -> some View {
        let on = interests.contains(interest.id)
        return Button {
            if on { interests.removeAll { $0 == interest.id } } else { interests.append(interest.id) }
        } label: {
            Text(interest.label)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(on ? Theme.background : .white)
            .padding(.horizontal, 16)
            .frame(minHeight: 44)
            .background(on ? Theme.yellow : .clear, in: Capsule())
            .overlay(Capsule().stroke(on ? Theme.yellow : Theme.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // MARK: - Chave da IA

    private var keyStep: some View {
        VStack(alignment: .leading, spacing: 18) {
            header(stepNumber: 3) { step = .interests }
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Conecte a")
                            Text("sua IA.").foregroundStyle(Theme.yellow)
                        }
                        .font(.system(size: 30, weight: .black))
                        .tracking(-0.9)
                        Text("A Ana e o Peter falam usando a sua própria chave. O Gemini é grátis e é o mais fácil para começar.")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Theme.muted)
                    }
                    Picker("IA", selection: $provider) {
                        ForEach(Provider.visible) { Text($0.shortName).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    APIKeyView(provider: provider) { saved in
                        if saved { finish() }
                    }
                    .id(provider)
                }
                .padding(.bottom, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            Button("Pular por enquanto") { finish() }
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private func finish() {
        Profile.shared.save(name: cleanName, photo: photo, interests: interests)
        onFinish()
    }

    // MARK: - Peças

    private func header(stepNumber: Int, back: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            Button(action: back) {
                Image(systemName: editing && stepNumber == 1 ? "xmark" : "chevron.left")
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 44, height: 44)
                    .background(Theme.card, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(editing && stepNumber == 1 ? "Fechar" : "Voltar")
            HStack(spacing: 6) {
                ForEach(1...totalSteps, id: \.self) { n in
                    Capsule().fill(n <= stepNumber ? Theme.yellow : Theme.border).frame(height: 6)
                }
            }
            .accessibilityHidden(true)
            Text("\(stepNumber) de \(totalSteps)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Theme.muted)
        }
    }

    private func primaryButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text(title)
                Image(systemName: "arrow.right")
            }
            .font(.system(size: 16, weight: .heavy))
            .foregroundStyle(Theme.background)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Theme.yellow, in: Capsule())
            .shadow(color: Theme.yellow.opacity(0.35), radius: 9, y: 6)
        }
        .buttonStyle(.plain)
    }

    private func disabledButton(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 16, weight: .heavy))
            .foregroundStyle(Theme.muted)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Theme.border, in: Capsule())
    }
}

/// Coloca os itens lado a lado e quebra a linha quando não cabem.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row { var items: [Int] = []; var y: CGFloat = 0; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if !rows[rows.count - 1].items.isEmpty, rows[rows.count - 1].width + spacing + size.width > width {
                let last = rows[rows.count - 1]
                rows.append(Row(y: last.y + last.height + spacing))
            }
            var row = rows[rows.count - 1]
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}
