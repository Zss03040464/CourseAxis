import Foundation

enum EventDraftError: LocalizedError {
    case missingTitle, invalidTime, invalidDate, missingTerm, invalidWeeks, invalidWeekday
    var errorDescription: String? {
        switch self {
        case .missingTitle: return "请填写名称"
        case .invalidTime: return "请填写有效的起止时间，且结束必须晚于开始"
        case .invalidDate: return "单次事件需要 YYYY-MM-DD 日期"
        case .missingTerm: return "周期事件需要先建立学期"
        case .invalidWeeks: return "请填写有效周次，例如 1-18周 或 1-17周(单)"
        case .invalidWeekday: return "星期必须为 1 到 7"
        }
    }
}

struct EventDraft: Equatable {
    var title = ""
    var kind: EventKind = .CUSTOM
    var recurrence: Recurrence = .SINGLE
    var date = LocalValue.dateString(Date())
    var weekday = 1
    var weeksText = "1-18周"
    var startTime = "08:00"
    var endTime = "09:00"
    var teacher = ""
    var location = ""
    var campus = ""
    var courseNature = "其他"
    var totalHours = ""
    var credits = ""
    var notes = ""
    var startSection: Int?
    var endSection: Int?

    init() {}
    init(event: ScheduleEvent) {
        title = event.title; kind = event.kind; recurrence = event.recurrence
        date = event.date ?? LocalValue.dateString(Date()); weekday = event.weekday ?? 1
        weeksText = WeekParser.format(event.weeks); startTime = event.startTime ?? "08:00"; endTime = event.endTime ?? "09:00"
        teacher = event.teacher; location = event.location; campus = event.campus
        let noteParts = event.notes.split(separator: "；").map(String.init)
        courseNature = event.courseNature
        if courseNature.isEmpty,let nature = noteParts.first(where: { $0.hasPrefix("课程性质：") }) { courseNature = String(nature.dropFirst(5)) }
        totalHours = event.totalHours;credits = event.credits
        notes = noteParts.filter { !$0.hasPrefix("课程性质：") }.joined(separator: "；")
        startSection = event.startSection; endSection = event.endSection
    }

    func makeEvent(existingID: String? = nil, activeTerm: Term?) throws -> ScheduleEvent {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { throw EventDraftError.missingTitle }
        guard let start = LocalValue.minutes(startTime), let end = LocalValue.minutes(endTime), end > start else { throw EventDraftError.invalidTime }
        let termID: String?
        let eventDate: String?
        let eventWeeks: Set<Int>
        let eventWeekday: Int?
        switch recurrence {
        case .SINGLE:
            guard LocalValue.date(date) != nil else { throw EventDraftError.invalidDate }
            termID = activeTerm?.id; eventDate = date; eventWeeks = []; eventWeekday = nil
        case .WEEKLY:
            guard let activeTerm else { throw EventDraftError.missingTerm }
            guard (1...7).contains(weekday) else { throw EventDraftError.invalidWeekday }
            let weeks = WeekParser.parse(weeksText, maxWeek: activeTerm.totalWeeks)
            guard !weeks.isEmpty else { throw EventDraftError.invalidWeeks }
            termID = activeTerm.id; eventDate = nil; eventWeeks = weeks; eventWeekday = weekday
        }
        return ScheduleEvent(
            id: existingID ?? StableID.make("user", UUID().uuidString), kind: kind, recurrence: recurrence,
            title: cleanTitle, teacher: teacher.trimmingCharacters(in: .whitespacesAndNewlines),
            location: location.trimmingCharacters(in: .whitespacesAndNewlines), campus: campus.trimmingCharacters(in: .whitespacesAndNewlines), notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            termId: termID, date: eventDate, weekday: eventWeekday, weeks: eventWeeks,
            startTime: LocalValue.timeString(start), endTime: LocalValue.timeString(end),
            startSection: startSection, endSection: endSection, timeRuleId: nil,
            timeSource: .USER, resolutionStatus: .RESOLVED, colorKey: StableID.colorKey(cleanTitle), updatedAt: LocalValue.isoNow(),courseNature:courseNature.trimmingCharacters(in:.whitespacesAndNewlines),totalHours:totalHours.trimmingCharacters(in:.whitespacesAndNewlines),credits:credits.trimmingCharacters(in:.whitespacesAndNewlines)
        )
    }
}
