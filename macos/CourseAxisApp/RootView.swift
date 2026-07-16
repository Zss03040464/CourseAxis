import SwiftUI

private extension PrimaryNavigationDestination {
    var title: String {
        switch self {
        case .week: "周课表"
        case .importSchool: "教务导入"
        case .settings: "设置"
        }
    }

    var icon: String {
        switch self {
        case .week: "calendar"
        case .importSchool: "square.and.arrow.down.fill"
        case .settings: "gearshape.fill"
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selection: PrimaryNavigationDestination? = PrimaryNavigationPolicy.defaultDestination
    @State private var editing: ScheduleEvent?
    @State private var adding = false
    @State private var defaultDate = Date()
    @State private var deleting: ScheduleEvent?

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                HStack(spacing: 11) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11)
                            .fill(LinearGradient(colors: [CourseAxisDesign.accent, .purple.opacity(0.78)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 38, height: 38)
                        Image(systemName: "calendar.day.timeline.leading").foregroundStyle(.white).font(.headline)
                    }
                    VStack(alignment: .leading, spacing: 1) { Text("课轴").font(.headline); Text("真实时间课程表").font(.caption2).foregroundStyle(.secondary) }
                    Spacer()
                }.padding(14)
                List(selection: $selection) {
                    Section("工作台") {
                        ForEach(PrimaryNavigationDestination.allCases) { item in
                            sidebarRow(item)
                        }
                    }
                }.listStyle(.sidebar)
            }
        } detail: {
            Group {
                switch selection ?? PrimaryNavigationPolicy.defaultDestination {
                case .week: WeekTimelineView(onEdit: { editing = $0 }, onAdd: openAdd, onDuplicate:model.duplicate, onDelete:{deleting=$0})
                case .importSchool: AcademicImportView()
                case .settings: SettingsView()
                }
            }
            .toolbar {
                ToolbarItemGroup {
                    Button { defaultDate = Date(); adding = true } label: { Label("添加事件", systemImage: "plus") }.keyboardShortcut("n",modifiers:.command)
                    Menu {
                        Button("JSON 导入", action: model.importJSON)
                        Button("JSON 导出", action: model.exportJSON)
                    } label: { Label("数据", systemImage: "arrow.up.arrow.down.square") }
                }
            }
        }
        .sheet(isPresented: Binding(get: { adding || editing != nil }, set: { if !$0 { adding = false; editing = nil } })) {
            EventEditorView(event: editing, defaultDate: defaultDate) { adding = false; editing = nil }
                .environmentObject(model)
        }
        .alert("操作失败", isPresented: Binding(get: { model.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil } })) { Button("确定", role: .cancel) {} } message: { Text(model.errorMessage ?? "") }
        .alert("已完成", isPresented: Binding(get: { model.noticeMessage != nil }, set: { if !$0 { model.noticeMessage = nil } })) { Button("确定", role: .cancel) {} } message: { Text(model.noticeMessage ?? "") }
        .alert("删除事件？",isPresented:Binding(get:{deleting != nil},set:{if !$0{deleting=nil}})){Button("取消",role:.cancel){};Button("删除",role:.destructive){if let deleting{model.delete(deleting);self.deleting=nil}}}message:{Text("将删除“\(deleting?.title ?? "")”。该删除会同步到其他设备。")}
        .onReceive(NotificationCenter.default.publisher(for:.courseAxisOpenWeek)) { _ in selection = .week }
    }
    private func openAdd(_ date:Date){defaultDate=date;adding=true}
    private func sidebarRow(_ item: PrimaryNavigationDestination) -> some View {
        Label(item.title, systemImage: item.icon).tag(item)
            .font(.body.weight(selection == item ? .semibold : .regular))
            .padding(.vertical, 3)
    }
}
