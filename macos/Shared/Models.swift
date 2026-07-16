import Foundation

enum EventKind: String, Codable, CaseIterable { case COURSE, EXAM, CUSTOM, ADJUSTMENT }
enum Recurrence: String, Codable, CaseIterable { case SINGLE, WEEKLY }
enum TimeSource: String, Codable { case EXPLICIT, SECTION_RULE, USER, DATE_OVERRIDE, UNRESOLVED }
enum ResolutionStatus: String, Codable { case RESOLVED, UNRESOLVED, NEEDS_REVIEW }
enum TimeRuleSource: String, Codable { case JIAOWU_DOM, JIAOWU_EXPLICIT, USER, MIGRATED }
enum EntityType: String, Codable { case EVENT, TERM, TIME_RULE }

struct SectionTime: Codable, Equatable, Hashable { var section:Int; var start:String; var end:String }
struct Term: Codable, Equatable, Identifiable {
    var id:String; var name:String; var providerId:String?=nil; var school:String=""; var campus:String=""; var academicYear:String=""; var semesterCode:String=""; var firstWeekMonday:String; var totalWeeks:Int=20; var timeZone:String="Asia/Shanghai"; var updatedAt:String=LocalValue.isoNow()
}
struct TimeRule: Codable, Equatable, Identifiable {
    var id:String; var termId:String; var school:String=""; var campus:String=""; var source:TimeRuleSource = .USER; var sections:[SectionTime]=[]; var updatedAt:String=LocalValue.isoNow()
}
struct ScheduleEvent: Codable, Equatable, Identifiable {
    var id:String; var externalId:String?=nil; var kind:EventKind; var recurrence:Recurrence; var title:String; var teacher:String=""; var location:String=""; var campus:String=""; var notes:String=""; var termId:String?=nil; var date:String?=nil; var weekday:Int?=nil; var weeks:Set<Int>=[]; var startTime:String?=nil; var endTime:String?=nil; var startSection:Int?=nil; var endSection:Int?=nil; var timeRuleId:String?=nil; var timeSource:TimeSource = .UNRESOLVED; var resolutionStatus:ResolutionStatus = .UNRESOLVED; var colorKey:Int=0; var updatedAt:String=LocalValue.isoNow(); var courseNature:String=""; var totalHours:String=""; var credits:String=""
    var isResolved:Bool { resolutionStatus == .RESOLVED && LocalValue.minutes(startTime) != nil && LocalValue.minutes(endTime) != nil }
}

extension ScheduleEvent {
    private enum CodingKeys:String,CodingKey { case id,externalId,kind,recurrence,title,teacher,location,campus,notes,termId,date,weekday,weeks,startTime,endTime,startSection,endSection,timeRuleId,timeSource,resolutionStatus,colorKey,updatedAt,courseNature,totalHours,credits }
    init(from decoder:Decoder)throws{
        let values=try decoder.container(keyedBy:CodingKeys.self)
        id=try values.decode(String.self,forKey:.id);externalId=try values.decodeIfPresent(String.self,forKey:.externalId);kind=try values.decode(EventKind.self,forKey:.kind);recurrence=try values.decode(Recurrence.self,forKey:.recurrence);title=try values.decode(String.self,forKey:.title)
        teacher=try values.decodeIfPresent(String.self,forKey:.teacher) ?? "";location=try values.decodeIfPresent(String.self,forKey:.location) ?? "";campus=try values.decodeIfPresent(String.self,forKey:.campus) ?? "";notes=try values.decodeIfPresent(String.self,forKey:.notes) ?? ""
        termId=try values.decodeIfPresent(String.self,forKey:.termId);date=try values.decodeIfPresent(String.self,forKey:.date);weekday=try values.decodeIfPresent(Int.self,forKey:.weekday);weeks=try values.decodeIfPresent(Set<Int>.self,forKey:.weeks) ?? []
        startTime=try values.decodeIfPresent(String.self,forKey:.startTime);endTime=try values.decodeIfPresent(String.self,forKey:.endTime);startSection=try values.decodeIfPresent(Int.self,forKey:.startSection);endSection=try values.decodeIfPresent(Int.self,forKey:.endSection);timeRuleId=try values.decodeIfPresent(String.self,forKey:.timeRuleId)
        timeSource=try values.decodeIfPresent(TimeSource.self,forKey:.timeSource) ?? .UNRESOLVED;resolutionStatus=try values.decodeIfPresent(ResolutionStatus.self,forKey:.resolutionStatus) ?? .UNRESOLVED;colorKey=try values.decodeIfPresent(Int.self,forKey:.colorKey) ?? 0;updatedAt=try values.decodeIfPresent(String.self,forKey:.updatedAt) ?? LocalValue.isoNow()
        let legacyNature=notes.split(separator:"；").map(String.init).first{$0.hasPrefix("课程性质：")}.map{String($0.dropFirst(5))} ?? ""
        courseNature=try values.decodeIfPresent(String.self,forKey:.courseNature) ?? legacyNature;totalHours=try values.decodeIfPresent(String.self,forKey:.totalHours) ?? "";credits=try values.decodeIfPresent(String.self,forKey:.credits) ?? ""
    }
    func encode(to encoder:Encoder)throws{
        var values=encoder.container(keyedBy:CodingKeys.self)
        try values.encode(id,forKey:.id);try values.encodeIfPresent(externalId,forKey:.externalId);try values.encode(kind,forKey:.kind);try values.encode(recurrence,forKey:.recurrence);try values.encode(title,forKey:.title);try values.encode(teacher,forKey:.teacher);try values.encode(location,forKey:.location);try values.encode(campus,forKey:.campus);try values.encode(notes,forKey:.notes);try values.encode(courseNature,forKey:.courseNature);try values.encode(totalHours,forKey:.totalHours);try values.encode(credits,forKey:.credits)
        try values.encodeIfPresent(termId,forKey:.termId);try values.encodeIfPresent(date,forKey:.date);try values.encodeIfPresent(weekday,forKey:.weekday);try values.encode(weeks,forKey:.weeks);try values.encodeIfPresent(startTime,forKey:.startTime);try values.encodeIfPresent(endTime,forKey:.endTime);try values.encodeIfPresent(startSection,forKey:.startSection);try values.encodeIfPresent(endSection,forKey:.endSection);try values.encodeIfPresent(timeRuleId,forKey:.timeRuleId);try values.encode(timeSource,forKey:.timeSource);try values.encode(resolutionStatus,forKey:.resolutionStatus);try values.encode(colorKey,forKey:.colorKey);try values.encode(updatedAt,forKey:.updatedAt)
    }
}
struct Tombstone: Codable, Equatable, Hashable { var id:String; var entityType:EntityType; var deletedAt:String=LocalValue.isoNow() }
struct AppSettings: Codable, Equatable {
    var activeTermId: String? = nil
    var dayStart: String = "07:30"
    var dayEnd: String = "22:30"
    var showWeekends: Bool = true
    var widgetUpcomingCount: Int = 5
    var selectedAcademicProviderId: String = "nfu-zhengfang"

    init(
        activeTermId: String? = nil,
        dayStart: String = "07:30",
        dayEnd: String = "22:30",
        showWeekends: Bool = true,
        widgetUpcomingCount: Int = 5,
        selectedAcademicProviderId: String = "nfu-zhengfang"
    ) {
        self.activeTermId = activeTermId
        self.dayStart = dayStart
        self.dayEnd = dayEnd
        self.showWeekends = showWeekends
        self.widgetUpcomingCount = widgetUpcomingCount
        self.selectedAcademicProviderId = selectedAcademicProviderId
    }

    private enum CodingKeys: String, CodingKey {
        case activeTermId
        case dayStart
        case dayEnd
        case showWeekends
        case widgetUpcomingCount
        case selectedAcademicProviderId
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        activeTermId = try values.decodeIfPresent(String.self, forKey: .activeTermId)
        dayStart = try values.decodeIfPresent(String.self, forKey: .dayStart) ?? "07:30"
        dayEnd = try values.decodeIfPresent(String.self, forKey: .dayEnd) ?? "22:30"
        showWeekends = try values.decodeIfPresent(Bool.self, forKey: .showWeekends) ?? true
        widgetUpcomingCount = try values.decodeIfPresent(Int.self, forKey: .widgetUpcomingCount) ?? 5
        selectedAcademicProviderId = try values.decodeIfPresent(String.self, forKey: .selectedAcademicProviderId) ?? "nfu-zhengfang"
    }
}
struct ScheduleDocument: Codable, Equatable {
    var schemaVersion:Int=1; var documentId:String=UUID().uuidString; var updatedAt:String=LocalValue.isoNow(); var terms:[Term]=[]; var timeRules:[TimeRule]=[]; var events:[ScheduleEvent]=[]; var tombstones:[Tombstone]=[]; var settings:AppSettings=AppSettings()
    static let empty = ScheduleDocument()
    var activeTerm:Term? { settings.activeTermId.flatMap { id in terms.first { $0.id == id } } ?? terms.max { LocalValue.instant($0.updatedAt) < LocalValue.instant($1.updatedAt) } }
    func timeRule(for termId:String?) -> TimeRule? { guard let termId else { return nil }; return timeRules.filter{$0.termId==termId}.max{LocalValue.instant($0.updatedAt)<LocalValue.instant($1.updatedAt)} }
}
struct EventOccurrence: Identifiable, Equatable { var event:ScheduleEvent; var date:Date; var start:Int; var end:Int; var id:String { event.id+"@"+LocalValue.dateString(date) } }
struct SectionMatch: Equatable { var startSection:Int; var endSection:Int; var exact:Bool; var coverageMinutes:Int }
struct LanePlacement: Identifiable { var occurrence:EventOccurrence; var laneIndex:Int; var laneCount:Int; var id:String{occurrence.id} }
