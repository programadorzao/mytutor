import SwiftUI

/// Um assunto que você gosta. `english` vai para o prompt.
struct Interest: Identifiable, Hashable {
    let id: String
    let label: String
    let english: String

    static let all: [Interest] = [
        Interest(id: "travel", label: "Viagens", english: "travel"),
        Interest(id: "work", label: "Trabalho", english: "work and career"),
        Interest(id: "interview", label: "Entrevista de emprego", english: "job interviews"),
        Interest(id: "tech", label: "Tecnologia", english: "technology"),
        Interest(id: "movies", label: "Filmes e séries", english: "movies and TV series"),
        Interest(id: "music", label: "Música", english: "music"),
        Interest(id: "sports", label: "Esportes", english: "sports"),
        Interest(id: "games", label: "Games", english: "video games"),
        Interest(id: "food", label: "Comida", english: "food and cooking"),
        Interest(id: "books", label: "Livros", english: "books"),
        Interest(id: "business", label: "Negócios", english: "business"),
        Interest(id: "fitness", label: "Saúde e treino", english: "health and fitness"),
        Interest(id: "daily", label: "Dia a dia", english: "daily life"),
        Interest(id: "news", label: "Notícias", english: "the news"),
    ]
}

/// Seu perfil (nome, foto, interesses), guardado no aparelho.
@Observable
final class Profile {
    static let shared = Profile()
    static let minInterests = 3

    private(set) var name: String
    private(set) var interests: [String]
    private(set) var photo: UIImage?
    private(set) var onboarded: Bool

    private let defaults = UserDefaults.standard
    private let photoURL = URL.documentsDirectory.appending(path: "profile-photo.jpg")

    private init() {
        name = defaults.string(forKey: "profileName") ?? ""
        interests = defaults.stringArray(forKey: "profileInterests") ?? []
        onboarded = defaults.bool(forKey: "profileOnboarded")
        photo = (try? Data(contentsOf: photoURL)).flatMap(UIImage.init(data:))
    }

    /// Ingleses dos interesses escolhidos, na ordem da lista.
    var interestTopics: [String] {
        Interest.all.filter { interests.contains($0.id) }.map(\.english)
    }

    func save(name: String, photo: UIImage?, interests: [String]) {
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        self.interests = interests
        self.photo = photo
        onboarded = true
        defaults.set(self.name, forKey: "profileName")
        defaults.set(interests, forKey: "profileInterests")
        defaults.set(true, forKey: "profileOnboarded")
        if let data = photo?.jpegData(compressionQuality: 0.8) {
            try? data.write(to: photoURL)
        } else {
            try? FileManager.default.removeItem(at: photoURL)
        }
    }
}

extension UIImage {
    /// Recorte quadrado do centro, reduzido (a foto só aparece pequena no chat).
    func squareThumbnail(side: CGFloat = 300) -> UIImage {
        let crop = min(size.width, size.height)
        let scale = side / crop
        let target = CGSize(width: side, height: side)
        return UIGraphicsImageRenderer(size: target).image { _ in
            draw(in: CGRect(
                x: -(size.width - crop) / 2 * scale,
                y: -(size.height - crop) / 2 * scale,
                width: size.width * scale,
                height: size.height * scale
            ))
        }
    }
}
