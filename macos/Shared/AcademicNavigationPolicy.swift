import Foundation

enum AcademicNavigationDestination: Equatable {
    case normalNavigation
    case currentWebView
    case externalBrowser
    case blocked
}

enum AcademicNavigationPolicy {
    static func isEmbeddedURLAllowed(_ url: URL, provider: AcademicProvider) -> Bool {
        AcademicProviderPolicy.isAllowed(url, provider: provider)
    }

    static func destination(
        for url: URL,
        provider: AcademicProvider,
        hasTargetFrame: Bool
    ) -> AcademicNavigationDestination {
        if isEmbeddedURLAllowed(url, provider: provider) {
            return hasTargetFrame ? .normalNavigation : .currentWebView
        }
        guard
            let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
            ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
            components.host != nil,
            components.user == nil,
            components.password == nil
        else { return .blocked }
        return .externalBrowser
    }
}
