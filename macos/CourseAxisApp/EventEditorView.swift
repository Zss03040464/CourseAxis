import SwiftUI

struct EventEditorView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let event: ScheduleEvent?
    let defaultDate: Date
    let onDone: () -> Void
    @State private var draft: EventDraft
    @State private var validationError: String?
    @State private var pendingEvent: ScheduleEvent?
    @State private var conflictLines: [String] = []
    @State private var confirmDelete = false
    @State private var showAdvanced = false

    init(event: ScheduleEvent?, defaultDate: Date, onDone: @escaping () -> Void) {
        self.event = event; self.defaultDate = defaultDate; self.onDone = onDone
        var value = event.map(EventDraft.init(event:)) ?? EventDraft()
        if event == nil { value.date = LocalValue.dateString(defaultDate) }
        _draft = State(initialValue: value)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack { Text(event == nil ? "添加真实时间事件" : "编辑事件").font(.title2.bold()); Spacer(); Button("取消") { close() }; Button("保存", action: attemptSave).keyboardShortcut(.defaultAction) }.padding()
            Divider()
            Form {
                Section("基本信息") { TextField("名称", text: $draft.title);Picker("类型", selection: $draft.kind) { ForEach(EventKind.allCases, id: \.self) { Text(kindLabel($0)).tag($0) } }.pickerStyle(.segmented);Picker("重复", selection: $draft.recurrence) { Text("单次事件").tag(Recurrence.SINGLE); Text("每周课程").tag(Recurrence.WEEKLY) }.pickerStyle(.segmented) }
                Section("日期与真实时间") { if draft.recurrence == .SINGLE { TextField("日期 YYYY-MM-DD", text: $draft.date) } else { HStack { Picker("星期", selection: $draft.weekday) { ForEach(1...7,id:\.self){Text(verbatim: "周\("一二三四五六日".character(at:$0-1))").tag($0)} }; TextField("周次，例如 1-18周", text: $draft.weeksText) } };HStack { TextField("开始 HH:mm", text: $draft.startTime); TextField("结束 HH:mm", text: $draft.endTime) };Label("课程块位置仅由真实起止时间决定",systemImage:"clock.badge.checkmark").font(.caption).foregroundStyle(.secondary) }
                Section("课程信息") {
                    HStack { TextField("地点", text: $draft.location); TextField("教师", text: $draft.teacher) }
                    TextField("校区", text: $draft.campus)
                    if draft.kind == .COURSE {
                        HStack { TextField("课程性质（按教务原文）",text:$draft.courseNature);TextField("总学时",text:$draft.totalHours);TextField("学分",text:$draft.credits) }
                    }
                    TextField("备注", text: $draft.notes, axis: .vertical).lineLimit(3...6)
                }
                Section { DisclosureGroup("高级节次元数据",isExpanded:$showAdvanced){HStack { OptionalIntField(title: "起始节次（可空）", value: $draft.startSection); OptionalIntField(title: "结束节次（可空）", value: $draft.endSection) };Text("节次只用于学校语义和反向匹配，不参与布局。").font(.caption).foregroundStyle(.secondary)} }
                if let validationError { Text(validationError).foregroundStyle(.red) }
                if event != nil { Button("删除事件", role: .destructive) { confirmDelete = true } }
            }.formStyle(.grouped)
        }.frame(width: 620, height: 660)
        .alert("发现真实时间冲突", isPresented: Binding(get:{pendingEvent != nil},set:{if !$0{pendingEvent=nil}})) { Button("取消",role:.cancel){pendingEvent=nil};Button("仍然保存"){if let pendingEvent{model.save(pendingEvent);close()}} } message: { Text(conflictLines.joined(separator:"\n")) }
        .alert("确定删除？", isPresented: $confirmDelete) { Button("取消",role:.cancel){};Button("删除",role:.destructive){if let event{model.delete(event);close()}} }
    }

    private func attemptSave(){do{let value=try draft.makeEvent(existingID:event?.id,activeTerm:model.document.activeTerm);let conflicts=ScheduleEngine.conflicts(candidate:value,document:model.document);if conflicts.isEmpty{model.save(value);close()}else{pendingEvent=value;conflictLines=conflicts.prefix(8).map{"\($0.1.event.title) \(LocalValue.timeString($0.1.start))–\(LocalValue.timeString($0.1.end))，重叠 \(ScheduleEngine.overlapMinutes($0.0,$0.1)) 分钟"}}}catch{validationError=error.localizedDescription}}
    private func close(){dismiss();onDone()}
    private func kindLabel(_ kind:EventKind)->String{switch kind{case .COURSE:"课程";case .EXAM:"考试";case .CUSTOM:"自定义";case .ADJUSTMENT:"调课"}}
}

private struct OptionalIntField: View { let title:String;@Binding var value:Int?;var body:some View{TextField(title,text:Binding(get:{value.map(String.init) ?? ""},set:{value=Int($0)}))} }
