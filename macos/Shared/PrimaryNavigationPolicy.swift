import Foundation

enum PrimaryNavigationDestination: String, CaseIterable, Identifiable {
    case week
    case importSchool
    case settings

    var id: String { rawValue }
}

enum PrimaryNavigationPolicy {
    static let defaultDestination: PrimaryNavigationDestination = .week
}
