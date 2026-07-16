import Foundation

struct ReminderRequest: Identifiable, Equatable {
    var id:String
    var eventID:String
    var title:String
    var body:String
    var fireDate:Date
}

enum ReminderPlanner {
    static func requests(document:ScheduleDocument,now:Date,offsetsByEventID:[String:[Int]],horizonDays:Int=60)->[ReminderRequest] {
        let startDay=LocalValue.calendar.startOfDay(for:now)
        return (0...max(0,horizonDays)).flatMap { offset -> [ReminderRequest] in
            guard let date=LocalValue.calendar.date(byAdding:.day,value:offset,to:startDay) else{return[]}
            return ScheduleEngine.occurrences(on:date,document:document).flatMap { occurrence -> [ReminderRequest] in
                guard let startDate=LocalValue.calendar.date(bySettingHour:occurrence.start/60,minute:occurrence.start%60,second:0,of:date) else{return[]}
                return (offsetsByEventID[occurrence.event.id] ?? []).compactMap { minutes in
                    guard minutes>=0,let fire=LocalValue.calendar.date(byAdding:.minute,value:-minutes,to:startDate),fire>now else{return nil}
                    let location=occurrence.event.location.isEmpty ? "" : " · \(occurrence.event.location)"
                    return ReminderRequest(id:"\(occurrence.id)-\(minutes)",eventID:occurrence.event.id,title:occurrence.event.title,body:"\(LocalValue.timeString(occurrence.start)) 开始\(location)",fireDate:fire)
                }
            }
        }.sorted{$0.fireDate<$1.fireDate}
    }
}
