import Foundation

struct AcademicProvider: Codable, Equatable, Sendable {
    var schemaVersion: Int = 1
    var id: String
    var schoolName: String
    var vendor: String
    var allowedOrigin: String
    var pathPrefix: String
    var loginPath: String
    var homePath: String
    var requiresThirdPartyCookies: Bool
    var semesterCodes: [String: String] = [:]
    var adapterId: String = ""
    var sessionNamespace: String = ""
}

enum AcademicProviderPolicy {
    static func isAllowed(_ url: URL, provider: AcademicProvider) -> Bool {
        guard
            let originURL = URL(string: provider.allowedOrigin),
            let origin = URLComponents(url: originURL, resolvingAgainstBaseURL: false),
            let candidate = URLComponents(url: url, resolvingAgainstBaseURL: false),
            isValidOrigin(origin),
            isValidPrefix(provider.pathPrefix),
            isHTTPS(candidate),
            candidate.user == nil,
            candidate.password == nil,
            candidate.host?.lowercased() == origin.host?.lowercased(),
            effectivePort(candidate) == effectivePort(origin),
            let decodedPath = candidate.percentEncodedPath.removingPercentEncoding,
            !decodedPath.contains("\\"),
            !decodedPath.contains("\0"),
            !decodedPath.split(separator: "/", omittingEmptySubsequences: false).contains(where: { $0 == "." || $0 == ".." })
        else { return false }

        return decodedPath.hasPrefix(provider.pathPrefix)
    }

    private static func isValidOrigin(_ components: URLComponents) -> Bool {
        isHTTPS(components) &&
            components.host != nil &&
            components.user == nil &&
            components.password == nil &&
            components.query == nil &&
            components.fragment == nil &&
            (components.percentEncodedPath.isEmpty || components.percentEncodedPath == "/")
    }

    private static func isHTTPS(_ components: URLComponents) -> Bool {
        components.scheme?.lowercased() == "https" && (components.port == nil || components.port == 443)
    }

    private static func effectivePort(_ components: URLComponents) -> Int {
        components.port ?? 443
    }

    private static func isValidPrefix(_ prefix: String) -> Bool {
        prefix.hasPrefix("/") &&
            prefix.hasSuffix("/") &&
            !prefix.contains("\\") &&
            !prefix.split(separator: "/", omittingEmptySubsequences: false).contains(where: { $0 == "." || $0 == ".." })
    }
}
