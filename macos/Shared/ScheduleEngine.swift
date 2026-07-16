import Foundation

enum TimeResolver {
    static func resolve(_ sections:[Int],rule:TimeRule)->(Int,Int)?{let sorted=Array(Set(sections)).sorted();guard !sorted.isEmpty,!zip(sorted,sorted.dropFirst()).contains(where:{$1 != $0+1})else{return nil};let map=Dictionary(uniqueKeysWithValues:rule.sections.map{($0.section,$0)});guard let first=map[sorted.first!],let last=map[sorted.last!],sorted.allSatisfy({map[$0] != nil}),let start=LocalValue.minutes(first.start),let end=LocalValue.minutes(last.end),end>start else{return nil};return(start,end)}
    static func infer(start:Int,end:Int,rule:TimeRule)->SectionMatch?{guard end>start else{return nil};let ordered=rule.sections.sorted{$0.section<$1.section};for i in ordered.indices{for j in i..<ordered.count{let slice=Array(ordered[i...j]);if zip(slice,slice.dropFirst()).contains(where:{$1.section != $0.section+1}){break};if LocalValue.minutes(slice.first!.start)==start&&LocalValue.minutes(slice.last!.end)==end{return SectionMatch(startSection:slice.first!.section,endSection:slice.last!.section,exact:true,coverageMinutes:end-start)}}};let hit=ordered.filter{guard let a=LocalValue.minutes($0.start),let b=LocalValue.minutes($0.end)else{return false};return a<end&&start<b};guard let first=hit.first,let last=hit.last else{return nil};let coverage=hit.reduce(0){sum,s in guard let a=LocalValue.minutes(s.start),let b=LocalValue.minutes(s.end)else{return sum};return sum+max(0,min(end,b)-max(start,a))};return SectionMatch(startSection:first.section,endSection:last.section,exact:false,coverageMinutes:coverage)}
}

enum ScheduleEngine {
    static var today:String{LocalValue.dateString(Date())}
    static func dateFor(term:Term,week:Int,weekday:Int)->Date?{guard(1...term.totalWeeks).contains(week),(1...7).contains(weekday),let monday=LocalValue.date(term.firstWeekMonday)else{return nil};return LocalValue.calendar.date(byAdding:.day,value:(week-1)*7+weekday-1,to:monday)}
    static func weekFor(term:Term,date:Date)->Int?{guard let monday=LocalValue.date(term.firstWeekMonday)else{return nil};let start=LocalValue.calendar.startOfDay(for:monday),target=LocalValue.calendar.startOfDay(for:date);guard let days=LocalValue.calendar.dateComponents([.day],from:start,to:target).day,days>=0 else{return nil};let week=days/7+1;return(1...term.totalWeeks).contains(week) ? week:nil}
    static func occurrence(event:ScheduleEvent,document:ScheduleDocument,date:Date)->EventOccurrence?{guard let start=LocalValue.minutes(event.startTime),let end=LocalValue.minutes(event.endTime),end>start else{return nil};let day=LocalValue.calendar.startOfDay(for:date);let occurs:Bool;switch event.recurrence{case .SINGLE:occurs=event.date.flatMap(LocalValue.date).map{LocalValue.calendar.isDate($0,inSameDayAs:day)} ?? false;case .WEEKLY:guard let termId=event.termId,let term=document.terms.first(where:{$0.id==termId}),let week=weekFor(term:term,date:day),let weekday=event.weekday else{return nil};occurs=weekday==LocalValue.calendar.component(.weekday,from:day).mondayBased&&event.weeks.contains(week)};return occurs ? EventOccurrence(event:event,date:day,start:start,end:end):nil}
    static func occurrences(on date:Date,document:ScheduleDocument)->[EventOccurrence]{document.events.compactMap{occurrence(event:$0,document:document,date:date)}.sorted{($0.start,$0.end,$0.event.title)<($1.start,$1.end,$1.event.title)}}
    static func occurrences(on date:Date,document:ScheduleDocument,termID:String)->[EventOccurrence]{document.events.filter{$0.termId == termID}.compactMap{occurrence(event:$0,document:document,date:date)}.sorted{($0.start,$0.end,$0.event.title)<($1.start,$1.end,$1.event.title)}}
    static func occurrencesForWeek(term:Term,week:Int,document:ScheduleDocument)->[Date:[EventOccurrence]]{Dictionary(uniqueKeysWithValues:(1...7).compactMap{day in guard let date=dateFor(term:term,week:week,weekday:day)else{return nil};return(date,occurrences(on:date,document:document,termID:term.id))})}
    static func nextOccurrences(after now:Date,document:ScheduleDocument,limit:Int)->[EventOccurrence] {
        guard limit > 0 else{return[]}
        let today=LocalValue.calendar.startOfDay(for:now)
        let candidates=document.events.flatMap { event -> [EventOccurrence] in
            let dates:[Date]
            switch event.recurrence {
            case .SINGLE:
                dates=event.date.flatMap(LocalValue.date).map{[$0]} ?? []
            case .WEEKLY:
                guard let termID=event.termId,
                      let term=document.terms.first(where:{$0.id==termID}),
                      let weekday=event.weekday else{return[]}
                dates=event.weeks.sorted().compactMap{dateFor(term:term,week:$0,weekday:weekday)}
            }
            return dates
                .filter{LocalValue.calendar.startOfDay(for:$0) >= today}
                .compactMap{occurrence(event:event,document:document,date:$0)}
                .filter{occurrence in
                    guard let startsAt=occurrenceStartDate(occurrence) else{return false}
                    return startsAt > now
                }
        }
        return Array(candidates.sorted(by:occurrenceComesBefore).prefix(limit))
    }
    static func nextOccurrence(after now:Date,document:ScheduleDocument)->EventOccurrence? {
        nextOccurrences(after:now,document:document,limit:1).first
    }
    static func overlaps(_ a:EventOccurrence,_ b:EventOccurrence)->Bool{LocalValue.calendar.isDate(a.date,inSameDayAs:b.date)&&a.start<b.end&&b.start<a.end}
    static func overlapMinutes(_ a:EventOccurrence,_ b:EventOccurrence)->Int{overlaps(a,b) ? max(0,min(a.end,b.end)-max(a.start,b.start)):0}
    static func conflicts(candidate: ScheduleEvent, document: ScheduleDocument) -> [(EventOccurrence, EventOccurrence)] {
        let dates: [Date]
        switch candidate.recurrence {
        case .SINGLE:
            dates = candidate.date.flatMap(LocalValue.date).map { [$0] } ?? []
        case .WEEKLY:
            guard let termId = candidate.termId,
                  let term = document.terms.first(where: { $0.id == termId }),
                  let weekday = candidate.weekday else { return [] }
            dates = candidate.weeks.compactMap { dateFor(term: term, week: $0, weekday: weekday) }
        }
        var result: [(EventOccurrence, EventOccurrence)] = []
        for date in dates {
            guard let candidateOccurrence = occurrence(event: candidate, document: document, date: date) else { continue }
            for other in occurrences(on: date, document: document)
                where other.event.id != candidate.id && overlaps(candidateOccurrence, other) {
                result.append((candidateOccurrence, other))
            }
        }
        return result
    }
    static func assignLanes(_ items:[EventOccurrence])->[LanePlacement]{guard !items.isEmpty else{return[]};let sorted=items.sorted{($0.start,$0.end)<($1.start,$1.end)};var groups:[[EventOccurrence]]=[];var current:[EventOccurrence]=[];var groupEnd=sorted[0].end;for item in sorted{if !current.isEmpty&&item.start>=groupEnd{groups.append(current);current=[];groupEnd=item.end};current.append(item);groupEnd=max(groupEnd,item.end)};if !current.isEmpty{groups.append(current)};return groups.flatMap{group in var ends:[Int]=[];var temp:[(EventOccurrence,Int)]=[];for item in group{if let free=ends.firstIndex(where:{$0<=item.start}){ends[free]=item.end;temp.append((item,free))}else{temp.append((item,ends.count));ends.append(item.end)}};return temp.map{LanePlacement(occurrence:$0.0,laneIndex:$0.1,laneCount:max(1,ends.count))}}}
    private static func occurrenceStartDate(_ occurrence:EventOccurrence)->Date? {
        LocalValue.calendar.date(bySettingHour:occurrence.start/60,minute:occurrence.start%60,second:0,of:occurrence.date)
    }
    private static func occurrenceComesBefore(_ lhs:EventOccurrence,_ rhs:EventOccurrence)->Bool {
        guard let left=occurrenceStartDate(lhs),let right=occurrenceStartDate(rhs) else{return lhs.id<rhs.id}
        if left != right{return left<right}
        return (lhs.end,lhs.event.title,lhs.event.id)<(rhs.end,rhs.event.title,rhs.event.id)
    }
}
private extension Int { var mondayBased:Int{self==1 ? 7:self-1} }
