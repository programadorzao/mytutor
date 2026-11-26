import SwiftUI

/// Tela inicial: escolha do tutor, IA (na engrenagem) e começar/continuar.
struct HomeView: View {
    @Binding var provider: Provider
    @Binding var maleVoice: Bool
    var hasSaved: Bool
    /// Flashcards para revisar hoje.
    var dueCards: Int
    var error: String?
    var onStart: () -> Void
    var onContinue: () -> Void
    var onOpenCards: () -> Void
    var onOpenSettings: () -> Void
    var onUseFree: () -> Void

    private var tutor: String { maleVoice ? "peter" : "ana" }
    private var tutorName: String { maleVoice ? "Peter" : "Ana" }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            topBar
            hero
            headline
            quickFix
            tutorPicker
            Spacer(minLength: 0)
            if let error {
                VStack(spacing: 8) {
                    Text(error)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.danger)
                        .multilineTextAlignment(.center)
                    // Cota da voz ao vivo acabou: o modo grátis continua funcionando.
                    if Provider.visible.contains(.economy), provider != .economy, error.localizedCaseInsensitiveContains("cota") {
                        Button("Usar o modo grátis", systemImage: "leaf", action: onUseFree)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.yellow)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            actions
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
        .foregroundStyle(.white)
    }

    private var topBar: some View {
        HStack {
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(height: 34)
                .accessibilityLabel("mytutor")
            Spacer()
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Theme.card, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Configurações")
        }
    }

    /// Tutor recortado sobre um "sol" amarelo, com um balão de fala.
    private var hero: some View {
        ZStack(alignment: .bottom) {
            Circle().stroke(Theme.yellow.opacity(0.25), lineWidth: 1).frame(width: 300, height: 300)
            Circle().fill(Theme.yellow).frame(width: 250, height: 250)
            Image("\(tutor)_closed")
                .resizable()
                .frame(width: 400, height: 600)
                .frame(width: 250, height: 270, alignment: .top)
                .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 125, bottomTrailingRadius: 125))
                .accessibilityLabel(tutorName)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 270, alignment: .bottom)
        .clipped()
        .overlay(alignment: .topTrailing) {
            Text("Hi \(Prompt.learnerName)! What do you want to practice today?")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.background)
                .lineSpacing(2)
                .frame(maxWidth: 150, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(.white, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                .shadow(color: .black.opacity(0.35), radius: 14, y: 12)
                .rotationEffect(.degrees(3))
                .padding(.top, 34)
        }
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Fale inglês")
            Text("sem travar.").foregroundStyle(Theme.yellow)
        }
        .font(.system(size: 38, weight: .black))
        .tracking(-1.2)
        .padding(.top, 4)
    }

    private var quickFix: some View {
        VStack(alignment: .leading, spacing: 6) {
            Overline(text: "Quick fix")
            HStack(spacing: 10) {
                Text("I have 26 years").strikethrough().foregroundStyle(Theme.muted)
                Image(systemName: "arrow.right").foregroundStyle(Theme.yellow)
                Text("I'm 26 years old").foregroundStyle(Theme.yellow)
            }
            .font(.system(size: 15, weight: .semibold))
            .accessibilityElement(children: .combine)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Theme.card)
        .overlay(alignment: .top) { Rectangle().fill(Theme.yellow).frame(height: 3) }
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var tutorPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Com quem você quer praticar?")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
            HStack(spacing: 10) {
                tutorCard(key: "ana", name: "Ana", voice: "Voz feminina", selected: !maleVoice) { maleVoice = false }
                tutorCard(key: "peter", name: "Peter", voice: "Voz masculina", selected: maleVoice) { maleVoice = true }
            }
        }
    }

    private func tutorCard(key: String, name: String, voice: String, selected: Bool, pick: @escaping () -> Void) -> some View {
        Button(action: pick) {
            HStack(spacing: 10) {
                TutorFace(tutor: key, size: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text(name).font(.system(size: 15, weight: .bold))
                    Text(voice).font(.system(size: 11, weight: .medium)).foregroundStyle(Theme.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 60)
            .background(selected ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? Theme.yellow : Theme.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var actions: some View {
        HStack(spacing: 10) {
            Button(action: onStart) {
                Label("Começar com \(tutorName)", systemImage: "mic.fill")
                    .font(.system(size: 16, weight: .heavy))
                    .foregroundStyle(Theme.background)
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Theme.yellow, in: Capsule())
                    .shadow(color: Theme.yellow.opacity(0.35), radius: 9, y: 6)
            }
            .buttonStyle(.plain)
            Button(action: onOpenCards) {
                Image(systemName: "rectangle.stack.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(Theme.card, in: Circle())
                    .overlay(Circle().stroke(Theme.border, lineWidth: 2))
                    .overlay(alignment: .topTrailing) {
                        if dueCards > 0 {
                            Text("\(dueCards)")
                                .font(.system(size: 11, weight: .heavy))
                                .foregroundStyle(Theme.background)
                                .padding(.horizontal, 6)
                                .frame(minWidth: 20, minHeight: 20)
                                .background(Theme.yellow, in: Capsule())
                        }
                    }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(dueCards > 0 ? "Flashcards, \(dueCards) para revisar" : "Flashcards")
            if hasSaved {
                Button(action: onContinue) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Theme.card, in: Circle())
                        .overlay(Circle().stroke(Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Conversas salvas")
            }
        }
    }
}
