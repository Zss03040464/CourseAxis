import SwiftUI
import WidgetKit
import AppIntents

struct CourseAxisTimelineEntry: TimelineEntry { let date: Date; let model: WidgetScheduleModel;let configuration:CourseAxisWidgetConfigurationIntent }

struct CourseAxisTimelineProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CourseAxisTimelineEntry {
        let current=WidgetEventSummary(id:"preview",title:"数据库原理",startTime:"16:00",endTime:"18:00",location:"5教 301",teacher:"李老师",kind:.EXAM,date:Date(),dayLabel:"今天")
        let upcoming=WidgetEventSummary(id:"preview-next",title:"数据挖掘",startTime:"19:00",endTime:"20:25",location:"3教 204",teacher:"陈老师",kind:.COURSE,date:Date(),dayLabel:"今天",weeksText:"1-18")
        return CourseAxisTimelineEntry(date:Date(),model:WidgetScheduleModel(primary:current,upcoming:[upcoming],today:[current,upcoming],status:"正在进行"),configuration:CourseAxisWidgetConfigurationIntent())
    }
    func snapshot(for configuration:CourseAxisWidgetConfigurationIntent,in context:Context) async ->CourseAxisTimelineEntry{entry(configuration)}
    func timeline(for configuration:CourseAxisWidgetConfigurationIntent,in context:Context) async ->Timeline<CourseAxisTimelineEntry>{let value=entry(configuration);let document=loadDocument();return Timeline(entries:[value],policy:.after(WidgetScheduleModel.nextRefreshDate(document:document,now:value.date)))}
    private func entry(_ configuration:CourseAxisWidgetConfigurationIntent) -> CourseAxisTimelineEntry {
        let now = Date(); let document = loadDocument()
        return CourseAxisTimelineEntry(date: now, model: .make(document: document, now: now),configuration:configuration)
    }
    private func loadDocument() -> ScheduleDocument {
        guard let store = try? DocumentFileStore(),
              let document = try? store.load() else { return .empty }
        return document
    }
}

struct CourseAxisWidget: Widget {
    let kind = "CourseAxisWidget"
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: CourseAxisWidgetConfigurationIntent.self, provider: CourseAxisTimelineProvider()) { entry in
            CourseAxisWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetBackdrop() }
        }
        .configurationDisplayName("课轴")
        .description("在桌面显示当前课程与可调数量的未来日程。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

private struct WidgetBackdrop: View {
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.11, green: 0.16, blue: 0.32), Color(red: 0.24, green: 0.16, blue: 0.42)], startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().fill(Color.blue.opacity(0.28)).frame(width: 180, height: 180).blur(radius: 45).offset(x: 95, y: -80)
            Circle().fill(Color.purple.opacity(0.22)).frame(width: 150, height: 150).blur(radius: 50).offset(x: -100, y: 90)
        }
    }
}

struct CourseAxisWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CourseAxisTimelineEntry
    private var smallSelection:WidgetAgendaSelection{WidgetAgendaPolicy.make(model:entry.model,size:.small)}
    private var mediumSelection:WidgetAgendaSelection{WidgetAgendaPolicy.make(model:entry.model,size:.medium)}
    private var largeSelection:WidgetAgendaSelection{WidgetAgendaPolicy.make(model:entry.model,size:.large)}
    private var focus:WidgetEventSummary?{smallSelection.focus}
    private func title(_ item:WidgetEventSummary)->String{entry.configuration.resolvedPrivacyMode ? (item.kind == .EXAM ? "考试":"课程") : item.title}
    private func secondary(_ item:WidgetEventSummary)->String{
        guard !entry.configuration.resolvedPrivacyMode else{return""}
        return [item.location,item.teacher,item.weeksText].filter{!$0.isEmpty}.joined(separator:" · ")
    }

    var body: some View {
        Group { switch family { case .systemSmall: small; case .systemMedium: medium; default: large } }
            .foregroundStyle(.white).widgetURL(URL(string: "courseaxis://week"))
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack { Image(systemName: entry.model.primary == nil ? "clock.fill" : "play.circle.fill"); Text(entry.model.status).font(.caption.bold()); Spacer(); Image(systemName: "calendar.day.timeline.leading").foregroundStyle(.white.opacity(0.7)) }
                .foregroundStyle(.white.opacity(0.82))
            if let focus {
                Text(title(focus)).font(.headline).lineLimit(2)
                if entry.model.primary == nil { Text(focus.dayLabel).font(.caption2.bold()).foregroundStyle(.white.opacity(0.72)) }
                Spacer(minLength: 2)
                Text("\(focus.startTime)–\(focus.endTime)").font(.caption.monospacedDigit().weight(.semibold))
                if let target = entry.model.primary == nil ? date(focus.startTime, on: focus.date) : date(focus.endTime, on: focus.date) {
                    Text(target, style: entry.model.primary == nil ? .relative : .timer).font(.caption2).foregroundStyle(.white.opacity(0.72))
                }
                if !secondary(focus).isEmpty { Label(secondary(focus), systemImage: "person.crop.circle.fill").font(.caption2).lineLimit(1).foregroundStyle(.white.opacity(0.72)) }
            } else {
                Spacer(); Text("今天轻松一下").font(.headline); Text("没有已解析日程").font(.caption).foregroundStyle(.white.opacity(0.7)); Spacer()
            }
        }
    }

    private var medium: some View {
        VStack(alignment:.leading,spacing:6) {
            agendaHeader(title:"接下来",count:(mediumSelection.primary == nil ? 0:1)+mediumSelection.upcoming.count)
            Rectangle().fill(.white.opacity(0.16)).frame(height:1)
            if let current=mediumSelection.primary {
                WidgetAgendaRow(item:current,context:"现在",privacyMode:entry.configuration.resolvedPrivacyMode,emphasizeExams:entry.configuration.resolvedEmphasizeExams)
            }
            ForEach(mediumSelection.upcoming){item in
                WidgetAgendaRow(item:item,context:item.dayLabel,privacyMode:entry.configuration.resolvedPrivacyMode,emphasizeExams:entry.configuration.resolvedEmphasizeExams)
            }
            if mediumSelection.primary == nil && mediumSelection.upcoming.isEmpty {
                Spacer()
                Label("未来没有已解析日程",systemImage:"sparkles").font(.callout.weight(.medium)).foregroundStyle(.white.opacity(0.72))
                Spacer()
            }
        }
    }

    private var large: some View {
        VStack(alignment:.leading,spacing:8) {
            agendaHeader(title:"课轴 · 桌面日程",count:(largeSelection.primary == nil ? 0:1)+largeSelection.upcoming.count,large:true)
            Rectangle().fill(.white.opacity(0.16)).frame(height:1)
            if let current=largeSelection.primary {
                WidgetAgendaRow(item:current,context:"正在上课",privacyMode:entry.configuration.resolvedPrivacyMode,emphasizeExams:entry.configuration.resolvedEmphasizeExams)
                    .padding(.horizontal,9)
                    .padding(.vertical,6)
                    .background(.white.opacity(0.09),in:RoundedRectangle(cornerRadius:12))
            }
            if !largeSelection.upcoming.isEmpty {
                Text("未来最近 \(largeSelection.upcoming.count) 节")
                    .font(.caption2.bold())
                    .foregroundStyle(.white.opacity(0.62))
                ForEach(largeSelection.upcoming){item in
                    WidgetAgendaRow(item:item,context:item.dayLabel,privacyMode:entry.configuration.resolvedPrivacyMode,emphasizeExams:entry.configuration.resolvedEmphasizeExams)
                }
            } else if largeSelection.primary == nil {
                Spacer()
                HStack{Spacer();VStack(spacing:8){Image(systemName:"sparkles").font(.largeTitle);Text("未来没有已解析日程").font(.headline)}.foregroundStyle(.white.opacity(0.76));Spacer()}
                Spacer()
            }
            Spacer(minLength:0)
        }
    }

    @ViewBuilder private func agendaHeader(title:String,count:Int,large:Bool=false)->some View{
        HStack(spacing:10){
            ZStack{
                RoundedRectangle(cornerRadius:large ? 10:8).fill(.white.opacity(0.14)).frame(width:large ? 38:30,height:large ? 38:30)
                Image(systemName:"calendar.day.timeline.leading")
            }
            VStack(alignment:.leading,spacing:1){
                Text(title).font(large ? .title3.bold():.headline)
                Text(count == 0 ? entry.model.status:"\(count) 项 · \(entry.model.status)").font(.caption2).foregroundStyle(.white.opacity(0.66))
            }
            Spacer()
            Text(LocalValue.monthDayWeekdayText(entry.date))
                .font(large ? .callout.weight(.medium):.caption)
                .foregroundStyle(.white.opacity(0.72))
        }
    }

    private func date(_ time: String, on day: Date) -> Date? {
        guard let minute = LocalValue.minutes(time) else { return nil }
        return LocalValue.calendar.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: day)
    }
}

private struct WidgetAgendaRow:View {
    let item:WidgetEventSummary
    let context:String
    var privacyMode=false
    var emphasizeExams=true
    private var presentation:WidgetEventPresentation{.make(item:item,privacyMode:privacyMode)}
    private var details:String{[item.endTime,privacyMode ? "":item.location,privacyMode ? "":item.teacher,item.weeksText].filter{!$0.isEmpty}.joined(separator:" · ")}
    var body:some View{
        HStack(spacing:9){
            VStack(alignment:.leading,spacing:1){
                Text(context).font(.caption2.bold()).foregroundStyle(.white.opacity(0.62)).lineLimit(1)
                Text(item.startTime).font(.caption.monospacedDigit().weight(.semibold))
            }
            .frame(width:64,alignment:.leading)
            Capsule().fill(item.kind == .EXAM && emphasizeExams ? Color.orange:Color.cyan).frame(width:3,height:30)
            VStack(alignment:.leading,spacing:1){
                Text(presentation.title).font(.caption.weight(.semibold)).lineLimit(1)
                Text(details).font(.caption2).foregroundStyle(.white.opacity(0.62)).lineLimit(1)
            }
            Spacer(minLength:0)
        }
    }
}
