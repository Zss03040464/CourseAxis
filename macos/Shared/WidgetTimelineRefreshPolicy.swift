import Foundation

enum WidgetTimelineRefreshTrigger {
    case applicationLaunch
    case applicationReopen
    case applicationActivation
    case windowVisibilityChange
}

struct WidgetTimelineRefreshPolicy {
    static let minimumInterval: TimeInterval = 2

    static func shouldReload(
        for trigger: WidgetTimelineRefreshTrigger,
        lastReloadAt: Date? = nil,
        now: Date = Date()
    ) -> Bool {
        guard trigger != .windowVisibilityChange else { return false }
        guard let lastReloadAt else { return true }
        return now.timeIntervalSince(lastReloadAt) >= minimumInterval
    }
}
