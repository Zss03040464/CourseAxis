import Foundation

struct WidgetEventSummary: Equatable, Identifiable {
    var id: String
    var title: String
    var startTime: String
    var endTime: String
    var location: String
    var teacher: String
    var kind: EventKind
    var date: Date
    var dayLabel: String
    var weeksText: String = ""
}

struct WidgetEventPresentation:Equatable {
    var title:String
    var details:String

    static func make(item:WidgetEventSummary,privacyMode:Bool)->WidgetEventPresentation {
        let title=privacyMode ? (item.kind == .EXAM ? "考试":"课程") : item.title
        return WidgetEventPresentation(
            title:title,
            details:["\(item.startTime)–\(item.endTime)",privacyMode ? "":item.location,privacyMode ? "":item.teacher,item.weeksText]
                .filter{!$0.isEmpty}
                .joined(separator:" · ")
        )
    }
}

struct WidgetScheduleModel: Equatable {
    var primary: WidgetEventSummary?
    var upcoming: [WidgetEventSummary]
    var today: [WidgetEventSummary]
    var status: String
    var next: WidgetEventSummary? { upcoming.first }

    static func make(document: ScheduleDocument, now: Date) -> WidgetScheduleModel {
        let day = LocalValue.calendar.startOfDay(for: now)
        let minute = LocalValue.calendar.component(.hour, from: now) * 60 + LocalValue.calendar.component(.minute, from: now)
        let todayOccurrences = ScheduleEngine.occurrences(on: day, document: document)
        let current = todayOccurrences.first { $0.start <= minute && minute < $0.end }
        let future = ScheduleEngine.nextOccurrences(after:now,document:document,limit:document.settings.widgetUpcomingCount)
        let summaries = todayOccurrences.map { summary($0,now:now) }
        return WidgetScheduleModel(
            primary: current.map { summary($0,now:now) },
            upcoming: future.map { summary($0,now:now) },
            today: summaries,
            status: current != nil ? "正在进行" : (!future.isEmpty ? "未来安排" : "未来没有已解析事件")
        )
    }

    static func nextRefreshDate(document: ScheduleDocument, now: Date) -> Date {
        let boundaries = (0...35).flatMap { offset -> [Date] in
            guard let date = LocalValue.calendar.date(byAdding: .day, value: offset, to: LocalValue.calendar.startOfDay(for: now)) else { return [] }
            return ScheduleEngine.occurrences(on: date, document: document).flatMap { occurrence in
                [occurrence.start, occurrence.end].compactMap { minute in
                    LocalValue.calendar.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: date)
                }
            }
        }.filter { $0 > now }.sorted()
        let midnight = LocalValue.calendar.date(byAdding: .day, value: 1, to: LocalValue.calendar.startOfDay(for: now))!
        return [boundaries.first, midnight].compactMap { $0 }.min() ?? midnight
    }

    private static func summary(_ occurrence: EventOccurrence,now:Date) -> WidgetEventSummary {
        let today=LocalValue.calendar.startOfDay(for:now),day=LocalValue.calendar.startOfDay(for:occurrence.date)
        let offset=LocalValue.calendar.dateComponents([.day],from:today,to:day).day ?? 0
        let label=offset==0 ? "今天" : offset==1 ? "明天" : LocalValue.monthDayWeekdayText(occurrence.date)
        return WidgetEventSummary(id: occurrence.id, title: occurrence.event.title, startTime: LocalValue.timeString(occurrence.start), endTime: LocalValue.timeString(occurrence.end), location: occurrence.event.location, teacher: occurrence.event.teacher, kind: occurrence.event.kind, date: occurrence.date,dayLabel:label,weeksText:WeekCardMetadata.compactWidgetWeeks(occurrence.event))
    }
}

enum WidgetAgendaSize { case small, medium, large }

struct WidgetAgendaSelection: Equatable {
    var primary: WidgetEventSummary?
    var upcoming: [WidgetEventSummary]
    var focus: WidgetEventSummary? { primary ?? upcoming.first }
}

enum WidgetAgendaPolicy {
    static func make(model:WidgetScheduleModel,size:WidgetAgendaSize)->WidgetAgendaSelection {
        let capacity:Int
        switch size {
        case .small:capacity=model.primary == nil ? 1:0
        case .medium:capacity=model.primary == nil ? 3:2
        case .large:capacity=5
        }
        return WidgetAgendaSelection(primary:model.primary,upcoming:Array(model.upcoming.prefix(capacity)))
    }
}
