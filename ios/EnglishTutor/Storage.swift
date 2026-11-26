import Foundation

/// Uma conversa guardada no histórico.
struct SavedConversation: Codable, Identifiable {
    var id = UUID()
    var savedAt: Date
    /// "ana" ou "peter".
    var tutor: String
    var lines: [Line]

    /// Primeira coisa que você disse (ou a primeira fala do tutor), para identificar a conversa.
    var title: String {
        let first = lines.first { $0.role == .me } ?? lines.first
        return first?.text.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Conversa"
    }
}

/// Histórico de conversas salvas, guardado no próprio aparelho.
enum Storage {
    private static let key = "savedConversations"
    /// Chave antiga, de quando só existia uma conversa salva.
    private static let legacyKey = "savedConversation"

    /// Todas as conversas, da mais recente para a mais antiga.
    static func all() -> [SavedConversation] {
        migrateLegacy()
        guard let data = UserDefaults.standard.data(forKey: key),
              let list = try? JSONDecoder().decode([SavedConversation].self, from: data) else { return [] }
        return list.sorted { $0.savedAt > $1.savedAt }
    }

    /// Salva (ou atualiza, se já existir com o mesmo id).
    static func save(_ conversation: SavedConversation) {
        var list = all().filter { $0.id != conversation.id }
        list.append(conversation)
        write(list)
    }

    static func delete(_ id: UUID) {
        write(all().filter { $0.id != id })
    }

    private static func write(_ list: [SavedConversation]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    private struct Legacy: Codable {
        let savedAt: Date
        let lines: [Line]
    }

    private static func migrateLegacy() {
        let defaults = UserDefaults.standard
        guard let data = defaults.data(forKey: legacyKey) else { return }
        defaults.removeObject(forKey: legacyKey)
        guard let old = try? JSONDecoder().decode(Legacy.self, from: data), !old.lines.isEmpty else { return }
        var list = (defaults.data(forKey: key)).flatMap { try? JSONDecoder().decode([SavedConversation].self, from: $0) } ?? []
        list.append(SavedConversation(savedAt: old.savedAt, tutor: "ana", lines: old.lines))
        write(list)
    }
}
