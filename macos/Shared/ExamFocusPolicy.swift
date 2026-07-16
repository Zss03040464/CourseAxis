import Foundation

struct ExamFocus: Equatable {
    let termId: String
    let weekNumber: Int
    let examCount: Int
}

enum ExamFocusPolicy {
    private struct Key: Hashable {
        let termId: String
        let weekNumber: Int
    }

    private struct Group {
        let focus: ExamFocus
        let lastDate: Date
    }

    static func resolve(_ document: ScheduleDocument) -> ExamFocus? {
        let terms = Dictionary(uniqueKeysWithValues: document.terms.map { ($0.id, $0) })
        var grouped: [Key: [Date]] = [:]

        for event in document.events where event.kind == .EXAM && event.recurrence == .SINGLE {
            guard
                let termId = event.termId,
                let term = terms[termId],
                let rawDate = event.date,
                let date = LocalValue.date(rawDate),
                let week = ScheduleEngine.weekFor(term: term, date: date)
            else { continue }
            grouped[Key(termId: termId, weekNumber: week), default: []].append(date)
        }

        let activeTermId = document.settings.activeTermId
        return grouped.map { key, dates in
            Group(
                focus: ExamFocus(termId: key.termId, weekNumber: key.weekNumber, examCount: dates.count),
                lastDate: dates.max()!
            )
        }.max { lhs, rhs in
            comparisonKey(lhs, activeTermId: activeTermId) < comparisonKey(rhs, activeTermId: activeTermId)
        }?.focus
    }

    private static func comparisonKey(_ group: Group, activeTermId: String?) -> ComparisonKey {
        ComparisonKey(
            examCount: group.focus.examCount,
            isActiveTerm: group.focus.termId == activeTermId ? 1 : 0,
            lastDate: group.lastDate,
            stableKey: "\(group.focus.termId):\(group.focus.weekNumber)"
        )
    }

    private struct ComparisonKey: Comparable {
        let examCount: Int
        let isActiveTerm: Int
        let lastDate: Date
        let stableKey: String

        static func < (lhs: ComparisonKey, rhs: ComparisonKey) -> Bool {
            if lhs.examCount != rhs.examCount { return lhs.examCount < rhs.examCount }
            if lhs.isActiveTerm != rhs.isActiveTerm { return lhs.isActiveTerm < rhs.isActiveTerm }
            if lhs.lastDate != rhs.lastDate { return lhs.lastDate < rhs.lastDate }
            return lhs.stableKey < rhs.stableKey
        }
    }
}
