import Foundation

enum ApplicationPresence: Equatable {
    case foreground
    case silentBackground
}

enum ApplicationPresencePolicy {
    static func desiredPresence(visibleUserWindowCount: Int) -> ApplicationPresence {
        visibleUserWindowCount > 0 ? .foreground : .silentBackground
    }
}
