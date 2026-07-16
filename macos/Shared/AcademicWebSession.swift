import Foundation
import WebKit

enum AcademicWebSessionError: LocalizedError, Equatable {
    case invalidNamespace(String)

    var errorDescription: String? {
        switch self {
        case .invalidNamespace(let providerID):
            "Provider \(providerID) 的会话命名空间无效"
        }
    }
}

enum AcademicWebSession {
    static let willClearData = Notification.Name("CourseAxisWillClearAcademicProviderData")
    static let didClearData = Notification.Name("CourseAxisDidClearAcademicProviderData")

    static func identifier(for provider: AcademicProvider) throws -> UUID {
        guard
            let identifier = UUID(uuidString: provider.sessionNamespace),
            identifier.uuidString.lowercased() == provider.sessionNamespace.lowercased()
        else { throw AcademicWebSessionError.invalidNamespace(provider.id) }
        return identifier
    }

    @MainActor
    static func dataStore(for provider: AcademicProvider) throws -> WKWebsiteDataStore {
        WKWebsiteDataStore(forIdentifier: try identifier(for: provider))
    }

    @MainActor
    static func prepare(
        provider: AcademicProvider,
        dataStore: WKWebsiteDataStore,
        completion: @escaping @MainActor @Sendable (Result<Void, Error>) -> Void
    ) {
        let identifier: UUID
        do { identifier = try self.identifier(for: provider) }
        catch { completion(.failure(error)); return }
        guard !AcademicWebSessionRegistry.contains(identifier) else {
            completion(.success(()))
            return
        }

        let finish: @MainActor @Sendable () -> Void = {
            AcademicWebSessionRegistry.register(providerID: provider.id, identifier: identifier)
            completion(.success(()))
        }
        let clearLegacyStore: @MainActor @Sendable () -> Void = {
            WKWebsiteDataStore.default().removeData(
                ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                modifiedSince: .distantPast,
                completionHandler: finish
            )
        }
        guard provider.id == legacyDefaultProviderID,
              let host = URL(string: provider.allowedOrigin)?.host?.lowercased()
        else {
            clearLegacyStore()
            return
        }

        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            let eligible = cookies.filter { cookie in
                let domain = cookie.domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
                let domainMatches = host == domain || host.hasSuffix("." + domain)
                let pathMatches = provider.pathPrefix.hasPrefix(cookie.path)
                return domainMatches && pathMatches
            }
            guard !eligible.isEmpty else {
                clearLegacyStore()
                return
            }
            let group = DispatchGroup()
            for cookie in eligible {
                group.enter()
                dataStore.httpCookieStore.setCookie(cookie) { group.leave() }
            }
            group.notify(queue: .main, execute: clearLegacyStore)
        }
    }

    @MainActor
    static func clear(provider: AcademicProvider) async throws {
        let identifier = try self.identifier(for: provider)
        try await clear(
            identifiers: [identifier],
            notifiedProviderIDs: [provider.id],
            clearLegacyDefault: provider.id == legacyDefaultProviderID
        )
    }

    @MainActor
    static func clearAll(providers: [AcademicProvider]) async throws {
        var identifiers = Set(try providers.map(identifier(for:)))
        identifiers.formUnion(AcademicWebSessionRegistry.identifiers())
        try await clear(
            identifiers: identifiers,
            notifiedProviderIDs: Set(providers.map(\.id)),
            clearLegacyDefault: true
        )
        AcademicWebSessionRegistry.clear()
    }

    @MainActor
    private static func clear(
        identifiers: Set<UUID>,
        notifiedProviderIDs: Set<String>,
        clearLegacyDefault: Bool
    ) async throws {
        NotificationCenter.default.post(
            name: willClearData,
            object: nil,
            userInfo: [providerIDsKey: notifiedProviderIDs]
        )
        for identifier in identifiers {
            let dataStore = WKWebsiteDataStore(forIdentifier: identifier)
            await withCheckedContinuation { continuation in
                dataStore.removeData(
                    ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                    modifiedSince: .distantPast,
                    completionHandler: { continuation.resume() }
                )
            }
        }
        if clearLegacyDefault {
            await withCheckedContinuation { continuation in
                WKWebsiteDataStore.default().removeData(
                    ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                    modifiedSince: .distantPast,
                    completionHandler: { continuation.resume() }
                )
            }
        }
        NotificationCenter.default.post(
            name: didClearData,
            object: nil,
            userInfo: [providerIDsKey: notifiedProviderIDs]
        )
    }

    static func notificationIncludesProvider(_ notification: Notification, providerID: String) -> Bool {
        (notification.userInfo?[providerIDsKey] as? Set<String>)?.contains(providerID) == true
    }

    private static let providerIDsKey = "providerIDs"
    private static let legacyDefaultProviderID = "nfu-zhengfang"
}

private enum AcademicWebSessionRegistry {
    static func contains(_ identifier: UUID) -> Bool {
        identifiers().contains(identifier)
    }

    static func register(providerID: String, identifier: UUID) {
        var values = UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:]
        values[providerID] = identifier.uuidString.lowercased()
        UserDefaults.standard.set(values, forKey: key)
    }

    static func identifiers() -> Set<UUID> {
        let values = UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:]
        return Set(values.values.compactMap(UUID.init(uuidString:)))
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }

    private static let key = "CourseAxis.academicProviderSessions"
}
