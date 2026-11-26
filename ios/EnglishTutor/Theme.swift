import SwiftUI

/// Cores do app (preto + amarelo).
enum Theme {
    static let background = Color(hex: 0x1D1D1B)
    static let card = Color(hex: 0x2B2B29)
    static let border = Color(hex: 0x3A3A37)
    static let yellow = Color(hex: 0xFFCC00)
    static let muted = Color(hex: 0xA7B3B7)
    static let myBubble = Color(hex: 0x3C5185)
    static let myAvatar = Color(hex: 0x4A65A3)
    static let danger = Color(hex: 0xFF8A80)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Rosto do tutor recortado num círculo amarelo. `frame`: closed, half, open ou blink.
struct TutorFace: View {
    var tutor: String
    var frame = "closed"
    var size: CGFloat

    var body: some View {
        Image("\(tutor)_\(frame)")
            .resizable()
            // Recorta pela silhueta da imagem "closed", que tem fundo transparente de verdade.
            .mask(Image("\(tutor)_closed").resizable())
            .frame(width: size * 2, height: size * 3)
            .offset(y: size * 0.84)
            .frame(width: size, height: size)
            .background(Theme.yellow)
            .clipShape(Circle())
            .accessibilityHidden(true)
    }
}

/// Sua foto, ou a inicial do nome num círculo azul.
struct LearnerFace: View {
    var size: CGFloat
    var name = Profile.shared.name
    var photo = Profile.shared.photo

    var body: some View {
        Group {
            if let photo {
                Image(uiImage: photo).resizable().scaledToFill()
            } else {
                Text(name.isEmpty ? "?" : String(name.prefix(1)).uppercased())
                    .font(.system(size: size * 0.4, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Theme.myAvatar)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

/// Rótulo pequeno em maiúsculas.
struct Overline: View {
    var text: String
    var color = Theme.muted

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 10, weight: .heavy))
            .tracking(1.4)
            .foregroundStyle(color)
    }
}
