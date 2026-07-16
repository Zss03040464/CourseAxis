import Foundation

struct SyncConfiguration: Codable, Equatable {
    var serverURL: URL
    var token: String
    var revision: Int64
}

enum SyncConfigurationPolicy {
    static func validate(serverURL: String, token: String, revision: Int64 = 0) -> SyncConfiguration? {
        let trimmed = serverURL
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard token.count >= 24,
              revision >= 0,
              let url = URL(string: trimmed),
              let host = url.host?.lowercased()
        else { return nil }
        let local = host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host.hasSuffix(".local") ||
            host.hasPrefix("10.") ||
            host.hasPrefix("192.168.") ||
            isPrivate172(host)
        guard url.scheme == "https" || (url.scheme == "http" && local) else { return nil }
        return SyncConfiguration(serverURL: url, token: token, revision: revision)
    }

    private static func isPrivate172(_ host: String) -> Bool {
        let pieces = host.split(separator: ".")
        guard pieces.count == 4,
              pieces[0] == "172",
              let second = Int(pieces[1])
        else { return false }
        return (16...31).contains(second)
    }
}

final class SyncSettingsStore {
    static let keychainService = "dev.shusenzheng64.courseaxis.self-hosted-sync"
    static let keychainAccount = "token"

    private let defaults: UserDefaults
    private let secretStore: KeychainSecretStoring

    init(
        defaults: UserDefaults = .standard,
        secretStore: KeychainSecretStoring = KeychainSecretStore()
    ) {
        self.defaults = defaults
        self.secretStore = secretStore
    }

    func load() throws -> SyncConfiguration? {
        try migrateLegacyTokenIfNeeded()
        guard let serverURL = defaults.string(forKey: Keys.serverURL),
              let token = try secretStore.read(
                service: Self.keychainService,
                account: Self.keychainAccount
              )
        else { return nil }
        guard let configuration = SyncConfigurationPolicy.validate(
            serverURL: serverURL,
            token: token,
            revision: Int64(defaults.integer(forKey: Keys.revision))
        ) else {
            try secretStore.delete(service: Self.keychainService, account: Self.keychainAccount)
            return nil
        }
        return configuration
    }

    func save(_ configuration: SyncConfiguration) throws {
        defer { defaults.removeObject(forKey: Keys.legacyToken) }
        try secretStore.save(
            configuration.token,
            service: Self.keychainService,
            account: Self.keychainAccount
        )
        defaults.set(configuration.serverURL.absoluteString, forKey: Keys.serverURL)
        defaults.set(configuration.revision, forKey: Keys.revision)
    }

    func updateRevision(_ revision: Int64) {
        defaults.set(revision, forKey: Keys.revision)
    }

    func clear() throws {
        defaults.removeObject(forKey: Keys.serverURL)
        defaults.removeObject(forKey: Keys.legacyToken)
        defaults.removeObject(forKey: Keys.revision)
        try secretStore.delete(service: Self.keychainService, account: Self.keychainAccount)
    }

    private func migrateLegacyTokenIfNeeded() throws {
        guard let legacyToken = defaults.string(forKey: Keys.legacyToken) else { return }
        defer { defaults.removeObject(forKey: Keys.legacyToken) }
        if try secretStore.read(service: Self.keychainService, account: Self.keychainAccount) != nil {
            return
        }
        let trimmed = legacyToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        try secretStore.save(
            trimmed,
            service: Self.keychainService,
            account: Self.keychainAccount
        )
    }

    private enum Keys {
        static let serverURL = "sync.serverURL"
        static let legacyToken = "sync.token"
        static let revision = "sync.revision"
    }
}
