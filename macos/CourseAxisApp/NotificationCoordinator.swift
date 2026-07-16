import Foundation
import UserNotifications

@MainActor
final class NotificationCoordinator {
    func scheduleExamReminders(document:ScheduleDocument) async throws -> Int {
        let center=UNUserNotificationCenter.current()
        let granted=try await center.requestAuthorization(options:[.alert,.sound])
        guard granted else { throw NSError(domain:"CourseAxis.Notifications",code:1,userInfo:[NSLocalizedDescriptionKey:"通知权限未开启，可在系统设置中稍后允许课轴通知"]) }
        let offsets=Dictionary(uniqueKeysWithValues:document.events.filter{$0.kind == .EXAM}.map{($0.id,[1440,120])})
        let requests=ReminderPlanner.requests(document:document,now:Date(),offsetsByEventID:offsets,horizonDays:180)
        let pending=await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers:pending.map(\.identifier).filter{$0.hasPrefix("courseaxis-")})
        for request in requests {
            let content=UNMutableNotificationContent();content.title=request.title;content.body=request.body;content.sound = .default
            let components=LocalValue.calendar.dateComponents([.year,.month,.day,.hour,.minute],from:request.fireDate)
            try await center.add(UNNotificationRequest(identifier:"courseaxis-\(request.id)",content:content,trigger:UNCalendarNotificationTrigger(dateMatching:components,repeats:false)))
        }
        return requests.count
    }
}
