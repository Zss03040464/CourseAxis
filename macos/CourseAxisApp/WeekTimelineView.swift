import SwiftUI

struct WeekTimelineView: View {
    @EnvironmentObject private var model: AppModel
    let onEdit: (ScheduleEvent) -> Void
    let onAdd: (Date) -> Void
    let onDuplicate: (ScheduleEvent) -> Void
    let onDelete: (ScheduleEvent) -> Void
    @State private var week = 1
    @State private var selectedTermID = ""
    @State private var mode: WeekEventMode = .all
    @State private var requestedExamFocus: ExamFocus?
    private let dayWidth: CGFloat = 172
    private let minuteHeight: CGFloat = 1.08

    private var selectedTerm: Term? {
        model.document.terms.first { $0.id == selectedTermID } ?? model.document.activeTerm ?? model.document.terms.first
    }

    var body: some View {
        if let term = selectedTerm {
            let start = LocalValue.minutes(model.document.settings.dayStart) ?? 450
            let end = max(start + 60, LocalValue.minutes(model.document.settings.dayEnd) ?? 1350)
            let days = model.document.settings.showWeekends ? 7 : 5
            ZStack {
                AmbientBackground().ignoresSafeArea()
                VStack(spacing:0) {
                    PageHeader(eyebrow:"第 \(week) 周",title:"周课表",subtitle:"\(term.name) · 课程与考试按真实时间统一展示",icon:"calendar") {
                        StatusBadge(text:"\(visibleItems(in: term).count) 项",color:CourseAxisDesign.accent,icon:mode == .exam ? "checkmark.seal.fill" : "calendar")
                    }
                    SurfacePanel {
                        HStack(spacing:12) {
                            Picker("学期",selection:$selectedTermID) { ForEach(model.document.terms) { Text($0.name).tag($0.id) } }.frame(minWidth:230)
                            Divider().frame(height:22)
                            Button { week=max(1,week-1) } label:{Image(systemName:"chevron.left")}.keyboardShortcut("[",modifiers:.command)
                            Picker("周次",selection:$week) { ForEach(1...term.totalWeeks,id:\.self) { Text("第 \($0) 周").tag($0) } }.frame(width:112)
                            Button { week=min(term.totalWeeks,week+1) } label:{Image(systemName:"chevron.right")}.keyboardShortcut("]",modifiers:.command)
                            Button { week=currentWeek(for:term) } label:{Label("回到本周",systemImage:"scope")}
                            Spacer()
                            Picker("显示",selection:Binding(
                                get:{mode},
                                set:{selectMode($0)}
                            )) {
                                ForEach(WeekEventMode.allCases) { item in Text(item.rawValue).tag(item) }
                            }.pickerStyle(.segmented).frame(width:210)
                            Label("虚线课程为非本周",systemImage:"circle.dashed").font(.caption).foregroundStyle(.secondary)
                        }
                    }.padding(.horizontal,CourseAxisDesign.pagePadding).padding(.bottom,12)
                    ScrollView([.horizontal,.vertical]) {
                        HStack(alignment:.top,spacing:0) {
                            TimeGutter(start:start,end:end,minuteHeight:minuteHeight)
                            ForEach(1...days,id:\.self) { weekday in
                                if let date=ScheduleEngine.dateFor(term:term,week:week,weekday:weekday) {
                                    DayColumn(date:date,items:WeekEventFilter.apply(WeekProjectionEngine.items(on:date,week:week,term:term,document:model.document),mode:mode),start:start,end:end,width:dayWidth,minuteHeight:minuteHeight,onEdit:onEdit,onAdd:onAdd,onDuplicate:onDuplicate,onDelete:onDelete)
                                }
                            }
                        }
                    }
                }
            }
            .onAppear { selectedTermID=term.id;week=currentWeek(for:term) }
            .onChange(of:selectedTermID) { _,id in
                guard let selected=model.document.terms.first(where:{$0.id==id}) else{return}
                model.activateTerm(selected)
                if let focus=requestedExamFocus,focus.termId==id {
                    week=focus.weekNumber.clamped(to:1...selected.totalWeeks)
                    requestedExamFocus=nil
                } else {
                    week=currentWeek(for:selected)
                }
            }
            .navigationTitle("周课表")
        } else {
            ContentUnavailableView("尚无学期",systemImage:"calendar.badge.exclamationmark",description:Text("请先在设置中建立学期或导入教务数据。"))
        }
    }

    private func currentWeek(for term:Term)->Int { (ScheduleEngine.weekFor(term:term,date:Date()) ?? 1).clamped(to:1...term.totalWeeks) }
    private func selectMode(_ selectedMode:WeekEventMode) {
        mode=selectedMode
        guard
            selectedMode == .exam,
            let focus=ExamFocusPolicy.resolve(model.document),
            let target=model.document.terms.first(where:{$0.id==focus.termId})
        else{return}
        requestedExamFocus=focus
        week=focus.weekNumber.clamped(to:1...target.totalWeeks)
        if selectedTermID != target.id {
            selectedTermID=target.id
        } else {
            model.activateTerm(target)
            requestedExamFocus=nil
        }
    }
    private func visibleItems(in term:Term)->[ProjectedOccurrence] {
        WeekEventFilter.apply(
            WeekProjectionEngine.items(inWeek:week,term:term,document:model.document,showWeekends:model.document.settings.showWeekends),
            mode:mode
        )
    }
}

private struct TimeGutter: View {
    let start:Int;let end:Int;let minuteHeight:CGFloat
    var body:some View { ZStack(alignment:.topLeading) { Color.clear.frame(width:58,height:CGFloat(end-start)*minuteHeight+48);ForEach(Array(stride(from:((start+59)/60)*60,through:end,by:60)),id:\.self) { minute in Text(String(format:"%02d:00",minute/60)).font(.caption.monospacedDigit()).foregroundStyle(.secondary).offset(x:4,y:42+CGFloat(minute-start)*minuteHeight) } } }
}

private struct DayColumn: View {
    let date:Date;let items:[ProjectedOccurrence];let start:Int;let end:Int;let width:CGFloat;let minuteHeight:CGFloat
    let onEdit:(ScheduleEvent)->Void;let onAdd:(Date)->Void;let onDuplicate:(ScheduleEvent)->Void;let onDelete:(ScheduleEvent)->Void
    private var placements:[LanePlacement] { ScheduleEngine.assignLanes(items.map{EventOccurrence(event:$0.event,date:$0.date,start:$0.start,end:$0.end)}) }
    private var weekdayText:String { let value=LocalValue.calendar.component(.weekday,from:date);return ["周日","周一","周二","周三","周四","周五","周六"][value-1] }
    var body:some View {
        VStack(spacing:0) {
            VStack { Text(weekdayText);Text(date.formatted(.dateTime.month().day().locale(Locale(identifier:"zh_CN")))).font(.caption).foregroundStyle(.secondary) }
                .frame(width:width,height:48)
                .background(LocalValue.calendar.isDateInToday(date) ? CourseAxisDesign.accent.opacity(0.13) : Color(nsColor:.controlBackgroundColor))
            ZStack(alignment:.topLeading) {
                Color.clear.contentShape(Rectangle()).onTapGesture{onAdd(date)}
                ForEach(Array(stride(from:((start+59)/60)*60,through:end,by:60)),id:\.self) { minute in Divider().offset(y:CGFloat(minute-start)*minuteHeight) }
                nowLine
                ForEach(placements) { placement in eventBlock(placement) }
            }.frame(width:width,height:CGFloat(end-start)*minuteHeight).overlay(Rectangle().stroke(.quaternary))
        }
    }
    @ViewBuilder private var nowLine:some View { if LocalValue.calendar.isDateInToday(date) { let minute=LocalValue.calendar.component(.hour,from:Date())*60+LocalValue.calendar.component(.minute,from:Date());if minute >= start && minute <= end { Rectangle().fill(Color.red).frame(width:width,height:2).offset(y:CGFloat(minute-start)*minuteHeight).overlay(alignment:.leading){Circle().fill(Color.red).frame(width:7,height:7).offset(x:-3)} } } }
    @ViewBuilder private func eventBlock(_ placement:LanePlacement)->some View {
        let clippedStart=max(start,placement.occurrence.start);let clippedEnd=min(end,placement.occurrence.end)
        if clippedEnd > clippedStart {
            let laneWidth=(width-6)/CGFloat(placement.laneCount);let active=items.first(where:{$0.id==placement.occurrence.id})?.occursThisWeek ?? true
            Button { onEdit(placement.occurrence.event) } label:{
                WeekEventCardContent(event:placement.occurrence.event,start:placement.occurrence.start,end:placement.occurrence.end,active:active)
                    .foregroundStyle(.white).padding(5).frame(width:max(30,laneWidth-2),height:max(26,CGFloat(clippedEnd-clippedStart)*minuteHeight),alignment:.topLeading)
                    .background(blockColor(for:placement.occurrence.event).opacity(active ? 1:0.3),in:RoundedRectangle(cornerRadius:7))
                    .overlay(RoundedRectangle(cornerRadius:7).stroke(.white.opacity(active ? 0.08:0.42),style:StrokeStyle(lineWidth:1,dash:active ? []:[4,3])))
            }.buttonStyle(.plain)
                .accessibilityLabel(accessibilityLabel(for:placement.occurrence.event,start:placement.occurrence.start,end:placement.occurrence.end))
                .accessibilityHint("点击编辑此日程")
                .contextMenu { Button("编辑"){onEdit(placement.occurrence.event)};Button("复制"){onDuplicate(placement.occurrence.event)};Divider();Button("删除",role:.destructive){onDelete(placement.occurrence.event)} }
                .offset(x:3+CGFloat(placement.laneIndex)*laneWidth,y:CGFloat(clippedStart-start)*minuteHeight)
        }
    }
    private func eventColor(_ key:Int)->Color { let colors:[Color]=[.blue,.purple,.green,.orange,.pink,.teal,.indigo,.brown];return colors[abs(key)%colors.count] }
    private func blockColor(for event:ScheduleEvent)->Color { event.kind == .EXAM ? Color(red:0.82,green:0.39,blue:0.18) : eventColor(event.colorKey) }
    private func accessibilityLabel(for event:ScheduleEvent,start:Int,end:Int)->String {
        [event.kind == .EXAM ? "考试" : event.kind.displayName,event.title,LocalValue.timeString(start)+"至"+LocalValue.timeString(end),event.location].filter{!$0.isEmpty}.joined(separator:"，")
    }
}

private struct WeekEventCardContent: View {
    let event:ScheduleEvent
    let start:Int
    let end:Int
    let active:Bool
    private var metadata:WeekCardMetadata { .make(event:event) }
    private var timeText:String { "\(LocalValue.timeString(start))–\(LocalValue.timeString(end))" }
    private var compactDetails:String { metadata.compactDetails(location:event.location) }

    var body:some View {
        ViewThatFits(in:.vertical) {
            VStack(alignment:.leading,spacing:1.5) {
                if event.kind == .EXAM { examBadge }
                title
                Text(timeText).font(.caption2.monospacedDigit().weight(.medium))
                if !event.location.isEmpty { Label(event.location,systemImage:"mappin.and.ellipse").font(.system(size:9,weight:.medium)).lineLimit(1) }
                Label(metadata.teacherText,systemImage:"person.fill").font(.system(size:9,weight:.medium)).lineLimit(1)
                Label(metadata.weeksText,systemImage:"calendar.badge.clock").font(.system(size:9,weight:.medium)).lineLimit(1)
                if event.kind == .COURSE {
                    Text([event.courseNature,event.totalHours.isEmpty ? "":"\(event.totalHours)学时",event.credits.isEmpty ? "":"\(event.credits)学分"].filter{!$0.isEmpty}.joined(separator:" · ")).font(.system(size:8,weight:.medium)).lineLimit(1)
                }
            }
            VStack(alignment:.leading,spacing:0) {
                if event.kind == .EXAM { examBadge }
                title
                Text(timeText).font(.system(size:9,weight:.semibold,design:.monospaced))
                Text(compactDetails).font(.system(size:8,weight:.medium)).lineLimit(1).minimumScaleFactor(0.55)
                if event.kind == .COURSE { Text([event.courseNature,event.totalHours.isEmpty ? "":"\(event.totalHours)学时",event.credits.isEmpty ? "":"\(event.credits)学分"].filter{!$0.isEmpty}.joined(separator:" · ")).font(.system(size:7.5,weight:.medium)).lineLimit(1).minimumScaleFactor(0.5) }
            }
        }
    }

    private var examBadge: some View {
        Text("考试").font(.system(size:8,weight:.bold)).padding(.horizontal,4).padding(.vertical,1).background(.white.opacity(0.2),in:Capsule())
    }

    private var title:some View {
        HStack(spacing:3) {
            Text(event.title).font(.caption.bold()).lineLimit(2)
            if !active { Text("非本周").font(.system(size:8,weight:.bold)).padding(.horizontal,3).padding(.vertical,1).background(.white.opacity(0.2),in:Capsule()) }
        }
    }
}

private extension Comparable { func clamped(to range:ClosedRange<Self>)->Self{min(max(self,range.lowerBound),range.upperBound)} }
