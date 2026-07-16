import Foundation

enum WeekEventMode: String, CaseIterable, Identifiable {
    case all = "全部"
    case course = "课程"
    case exam = "考试"

    var id: String { rawValue }
}

enum WeekEventFilter {
    static func apply(_ items: [ProjectedOccurrence], mode: WeekEventMode) -> [ProjectedOccurrence] {
        switch mode {
        case .all:
            items
        case .course:
            items.filter { $0.event.kind == .COURSE }
        case .exam:
            items.filter { $0.event.kind == .EXAM }
        }
    }
}
