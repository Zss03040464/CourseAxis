import Foundation

enum AppNavigationRoute: Equatable {
    case week

    static func parse(_ url: URL) -> AppNavigationRoute {
        guard url.scheme?.lowercased() == "courseaxis" else { return .week }
        return .week
    }
}

extension Notification.Name {
    static let courseAxisOpenWeek = Notification.Name("CourseAxisOpenWeek")
}
