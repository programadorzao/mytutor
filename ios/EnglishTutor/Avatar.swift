import SwiftUI

/// Tutor em fotos dentro de um círculo: troca entre boca fechada, entreaberta e aberta, e pisca sozinho.
/// `tutor` é o prefixo das imagens ("ana" ou "peter"); `mouth` vai de 0 a 1.
struct Avatar: View {
    var tutor: String
    var mouth: Double
    var size: CGFloat

    @State private var blinking = false

    var body: some View {
        TutorFace(tutor: tutor, frame: frame, size: size)
            .task { await blinkLoop() }
    }

    private var frame: String {
        if mouth < 0.08 { return blinking ? "blink" : "closed" }
        return mouth < 0.45 ? "half" : "open"
    }

    private func blinkLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(Double.random(in: 2.5...5)))
            blinking = true
            try? await Task.sleep(for: .milliseconds(120))
            blinking = false
        }
    }
}
