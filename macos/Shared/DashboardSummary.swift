import Foundation

struct DashboardSummary: Equatable {
    var today: [EventOccurrence]
    var current: EventOccurrence?
    var next: EventOccurrence?
    var upcomingSevenDays: Int
    var daysUntilActiveTerm: Int?
    var unresolvedCount: Int

    static func make(document: ScheduleDocument, now: Date) -> DashboardSummary {
        let startDay = LocalValue.calendar.startOfDay(for: now)
        let minute = LocalValue.calendar.component(.hour, from: now) * 60 + LocalValue.calendar.component(.minute, from: now)
        let today = ScheduleEngine.occurrences(on:startDay,document:document)
        let current = today.first { $0.start <= minute && minute < $0.end }
        var next: EventOccurrence?
        var sevenDayCount = 0
        for offset in 0...90 {
            guard let date = LocalValue.calendar.date(byAdding:.day,value:offset,to:startDay) else { continue }
            let occurrences = ScheduleEngine.occurrences(on:date,document:document)
            if offset <= 7 { sevenDayCount += occurrences.filter { offset > 0 || $0.end > minute }.count }
            if next == nil {
                next = occurrences.first { offset > 0 || $0.start > minute }
            }
        }
        let daysUntilTerm = document.activeTerm.flatMap { term -> Int? in
            guard let first = LocalValue.date(term.firstWeekMonday) else { return nil }
            let days = LocalValue.calendar.dateComponents([.day],from:startDay,to:LocalValue.calendar.startOfDay(for:first)).day
            return days.map { max(0,$0) }
        }
        return DashboardSummary(
            today:today,
            current:current,
            next:next,
            upcomingSevenDays:sevenDayCount,
            daysUntilActiveTerm:daysUntilTerm,
            unresolvedCount:document.events.filter { !$0.isResolved }.count
        )
    }
}
