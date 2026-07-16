import SwiftUI

struct EventDetailView:View {
    let event:ScheduleEvent
    let term:Term?
    let onEdit:()->Void
    let onDuplicate:()->Void
    let onDelete:()->Void
    var body:some View { ScrollView { VStack(alignment:.leading,spacing:16) {
        HStack { ZStack{RoundedRectangle(cornerRadius:12).fill(CourseAxisDesign.accent.opacity(0.14)).frame(width:48,height:48);Image(systemName:event.kind.symbolName).font(.title2).foregroundStyle(CourseAxisDesign.accent)};VStack(alignment:.leading){Text(event.title).font(.title2.bold());Text(event.kind.displayName).foregroundStyle(.secondary)} }
        Divider()
        DetailLine(icon:"clock",title:"真实时间",value:[event.date,event.startTime.flatMap{start in event.endTime.map{"\(start)–\($0)"}}].compactMap{$0}.joined(separator:" · "))
        if event.recurrence == .WEEKLY { DetailLine(icon:"repeat",title:"上课周次",value:"周\(event.weekday.map{String("一二三四五六日".character(at:$0-1))} ?? "?") · \(WeekParser.format(event.weeks))") }
        if !event.location.isEmpty { DetailLine(icon:"location",title:"地点",value:event.location) }
        if !event.teacher.isEmpty { DetailLine(icon:"person",title:"教师",value:event.teacher) }
        if event.kind == .COURSE {
            if !event.courseNature.isEmpty { DetailLine(icon:"checkmark.seal",title:"课程性质",value:event.courseNature) }
            if !event.totalHours.isEmpty { DetailLine(icon:"hourglass",title:"总学时",value:event.totalHours) }
            if !event.credits.isEmpty { DetailLine(icon:"graduationcap",title:"学分",value:event.credits) }
        }
        if let term { DetailLine(icon:"calendar",title:"学期",value:term.name) }
        if !event.notes.isEmpty { DetailLine(icon:"note.text",title:"备注",value:event.notes) }
        Spacer(minLength:8)
        HStack { Button("编辑",action:onEdit).buttonStyle(.borderedProminent);Button("复制",action:onDuplicate);Spacer();Button("删除",role:.destructive,action:onDelete) }
    }.padding(20) } }
}
private struct DetailLine:View { let icon:String;let title:String;let value:String;var body:some View{HStack(alignment:.top,spacing:11){Image(systemName:icon).foregroundStyle(CourseAxisDesign.accent).frame(width:20);VStack(alignment:.leading,spacing:3){Text(title).font(.caption).foregroundStyle(.secondary);Text(value.isEmpty ? "未填写":value).textSelection(.enabled)}}} }
