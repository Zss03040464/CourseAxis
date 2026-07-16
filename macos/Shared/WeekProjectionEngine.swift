import Foundation

struct ProjectedOccurrence: Identifiable, Equatable {
    var event: ScheduleEvent
    var date: Date
    var start: Int
    var end: Int
    var occursThisWeek: Bool
    var id: String { event.id + "@" + LocalValue.dateString(date) }
}

enum WeekProjectionEngine {
    static func items(inWeek week: Int, term: Term, document: ScheduleDocument, showWeekends: Bool) -> [ProjectedOccurrence] {
        let dayCount = showWeekends ? 7 : 5
        return (1...dayCount).flatMap { weekday -> [ProjectedOccurrence] in
            guard let date = ScheduleEngine.dateFor(term:term,week:week,weekday:weekday) else { return [] }
            return items(on:date,week:week,term:term,document:document)
        }
    }

    static func items(on date: Date, week: Int, term: Term, document: ScheduleDocument) -> [ProjectedOccurrence] {
        let calendarWeekday = LocalValue.calendar.component(.weekday, from: date)
        let weekday = calendarWeekday == 1 ? 7 : calendarWeekday - 1
        let raw: [ProjectedOccurrence] = document.events.compactMap { event -> ProjectedOccurrence? in
            guard event.termId == term.id,
                  let start = LocalValue.minutes(event.startTime),
                  let end = LocalValue.minutes(event.endTime), end > start else { return nil }
            switch event.recurrence {
            case .SINGLE:
                guard event.date.flatMap(LocalValue.date).map({ LocalValue.calendar.isDate($0, inSameDayAs: date) }) == true else { return nil }
                return ProjectedOccurrence(event:event,date:date,start:start,end:end,occursThisWeek:true)
            case .WEEKLY:
                guard event.weekday == weekday,
                      let lastWeek = event.weeks.max(),
                      week <= lastWeek else { return nil }
                return ProjectedOccurrence(event:event,date:date,start:start,end:end,occursThisWeek:event.weeks.contains(week))
            }
        }
        let active:[ProjectedOccurrence]=raw.filter{$0.occursThisWeek}
        let visible:[ProjectedOccurrence]=raw.filter { item in
            item.occursThisWeek || !active.contains { current in
                current.event.title == item.event.title && current.event.teacher == item.event.teacher && current.start < item.end && item.start < current.end
            }
        }
        return visible.sorted { lhs, rhs in
            if lhs.occursThisWeek != rhs.occursThisWeek { return lhs.occursThisWeek && !rhs.occursThisWeek }
            return (lhs.start,lhs.end,lhs.event.title,lhs.event.id) < (rhs.start,rhs.end,rhs.event.title,rhs.event.id)
        }
    }
}
