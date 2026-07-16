import Foundation

struct WeekCardMetadata: Equatable {
    var teacherText: String
    var weeksText: String

    static func make(event: ScheduleEvent) -> WeekCardMetadata {
        let teacher = event.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
        let weeks: String
        switch event.recurrence {
        case .SINGLE:
            weeks = "单次"
        case .WEEKLY:
            let formatted = formatWeeks(event.weeks)
            weeks = formatted.isEmpty ? "周次待确认" : formatted
        }
        return WeekCardMetadata(
            teacherText: teacher.isEmpty ? "教师待确认" : teacher,
            weeksText: weeks
        )
    }

    func compactDetails(location:String)->String {
        [teacherText,weeksText,location.trimmingCharacters(in:.whitespacesAndNewlines)]
            .filter{!$0.isEmpty}
            .joined(separator:" · ")
    }

    func fullDetails(event:ScheduleEvent)->[String] {
        var values=["教师：\(teacherText)"]
        let location=event.location.trimmingCharacters(in:.whitespacesAndNewlines)
        if !location.isEmpty { values.append("地点：\(location)") }
        if let start=event.startTime,let end=event.endTime { values.append("时间：\(start)–\(end)") }
        values.append("周次：\(weeksText)")
        guard event.kind == .COURSE else { return values }
        let nature=event.courseNature.trimmingCharacters(in:.whitespacesAndNewlines)
        let hours=event.totalHours.trimmingCharacters(in:.whitespacesAndNewlines)
        let credits=event.credits.trimmingCharacters(in:.whitespacesAndNewlines)
        if !nature.isEmpty { values.append("性质：\(nature)") }
        if !hours.isEmpty { values.append("总学时：\(hours)") }
        if !credits.isEmpty { values.append("学分：\(credits)") }
        return values
    }

    static func compactWidgetWeeks(_ event:ScheduleEvent)->String {
        guard event.recurrence == .WEEKLY,
              let first=event.weeks.min(),
              let last=event.weeks.max() else { return "" }
        return "\(first)-\(last)"
    }

    private static func formatWeeks(_ weeks:Set<Int>)->String {
        let values=weeks.sorted()
        if values.count>1,zip(values,values.dropFirst()).allSatisfy({$1-$0==2}),let first=values.first,let last=values.last {
            return "\(first)-\(last)周（\(first.isMultiple(of:2) ? "双":"单")）"
        }
        return WeekParser.format(weeks)
    }
}
