import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model:AppModel
    @State private var tab="terms"
    var body:some View{ZStack{AmbientBackground().ignoresSafeArea();VStack(spacing:0){PageHeader(eyebrow:"偏好与数据",title:"设置",subtitle:"管理学期、显示方式、数据与同步",icon:"gearshape.fill") { EmptyView() };SurfacePanel{Picker("设置分类",selection:$tab){Text("学期管理").tag("terms");Text("显示与数据").tag("general");Text("同步").tag("sync")}.pickerStyle(.segmented)}.padding(.horizontal,CourseAxisDesign.pagePadding).padding(.bottom,12);switch tab{case "terms":TermManagementView();case "sync":SyncSettingsView();default:GeneralSettingsView()}}}.navigationTitle("设置")}
}

private struct GeneralSettingsView:View{
    @EnvironmentObject private var model:AppModel
    @State private var dayStart="07:30"
    @State private var dayEnd="22:30"
    @State private var weekends=true
    @State private var widgetUpcomingCount=5
    @State private var showingClearSession=false
    @State private var showingClearAll=false
    @State private var showingPrivacyNotice=false

    var body:some View{
        let health=DataHealth.inspect(model.document)
        Form{
            Section("时间轴"){
                HStack{
                    TextField("起始 HH:mm",text:$dayStart)
                    TextField("结束 HH:mm",text:$dayEnd)
                    Toggle("显示周末",isOn:$weekends)
                }
            }
            Section("桌面小组件"){
                Stepper(value:$widgetUpcomingCount,in:1...5){
                    HStack{
                        Label("未来最近课程",systemImage:"rectangle.stack.badge.clock")
                        Spacer()
                        Text("\(widgetUpcomingCount) 节")
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                Text("当前课程和今天稍后的课程优先；其余位置会按真实时间补充未来课程。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Section{
                Button("保存显示与桌面设置",action:save)
                    .buttonStyle(.borderedProminent)
            }
            Section("数据健康"){
                Label(health.isHealthy ? "数据结构正常":"发现需要处理的数据问题",systemImage:health.isHealthy ? "checkmark.shield.fill":"exclamationmark.triangle.fill")
                    .foregroundStyle(health.isHealthy ? .green:.orange)
                Text("待确认 \(health.unresolvedEventCount) 项 · 重复学期 \(health.semanticDuplicateTermCount) 项 · 孤立记录 \(health.orphanEventIDs.count+health.orphanRuleIDs.count) 项")
                    .foregroundStyle(.secondary)
                if model.canUndoDestructiveChange{Button("恢复上一次删除",action:model.undoLastDeletion)}
            }
            Section("数据"){
                HStack{Button("JSON 导入",action:model.importJSON);Button("JSON 导出",action:model.exportJSON)}
            }
            Section("隐私与清除"){
                Text("仅清账号会删除教务 Cookie、WebStorage 与网页缓存；清除全部还会永久删除课表、同步令牌和迁移备份。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("查看隐私说明"){showingPrivacyNotice=true}
                HStack{
                    Button("仅清账号",role:.destructive){showingClearSession=true}
                    Button("清除全部本机数据",role:.destructive){showingClearAll=true}
                }
            }
        }
        .formStyle(.grouped)
        .padding()
        .onAppear(perform:load)
        .alert("隐私说明",isPresented:$showingPrivacyNotice){Button("知道了",role:.cancel){}}message:{Text("CourseAxis 默认不含分析、广告或崩溃上报。课表与考试保存在 App Group 中供主应用和 Widget 读取；教务 Cookie 留在 WKWebView。启用自托管同步后，仅课程 JSON 会发往你配置的服务器。")}
        .confirmationDialog("清除\(selectedProviderName)教务账号与会话？",isPresented:$showingClearSession,titleVisibility:.visible){Button("确认清除",role:.destructive,action:model.clearAcademicSession);Button("取消",role:.cancel){}}message:{Text("其他学校、课表和同步设置会保留；当前学校的 Cookie、WebStorage 与网页缓存会被删除。")}
        .confirmationDialog("清除全部本机数据？",isPresented:$showingClearAll,titleVisibility:.visible){Button("永久清除",role:.destructive,action:model.clearAllLocalData);Button("取消",role:.cancel){}}message:{Text("这会删除课表、教务会话、同步令牌和本机备份。请先导出需要保留的 JSON。")}
    }

    private func load(){
        let settings=model.document.settings
        dayStart=settings.dayStart
        dayEnd=settings.dayEnd
        weekends=settings.showWeekends
        widgetUpcomingCount=settings.widgetUpcomingCount
    }

    private func save(){
        model.updateSettings(AppSettings(
            activeTermId:model.document.settings.activeTermId,
            dayStart:dayStart,
            dayEnd:dayEnd,
            showWeekends:weekends,
            widgetUpcomingCount:widgetUpcomingCount,
            selectedAcademicProviderId:model.document.settings.selectedAcademicProviderId
        ))
    }

    private var selectedProviderName:String {
        model.academicProviders.first { $0.id == model.document.settings.selectedAcademicProviderId }?.schoolName
            ?? "当前学校"
    }
}

private struct SyncSettingsView:View{
    @EnvironmentObject private var model:AppModel
    @State private var serverURL="";@State private var token="";@State private var message="";private let store=SyncSettingsStore()
    var body:some View{Form{Section("自托管同步"){TextField("https://server.example",text:$serverURL);SecureField("令牌（至少 24 字符）",text:$token);HStack{Button("保存设置",action:save);Button("拉取"){run(push:false)};Button("上传"){run(push:true)}};if !message.isEmpty{Text(message).foregroundStyle(.secondary)}}}.formStyle(.grouped).padding().onAppear{if let c=try? store.load(){serverURL=c.serverURL.absoluteString;token=c.token}}}
    private func save(){guard let c=SyncConfigurationPolicy.validate(serverURL:serverURL,token:token)else{message="同步地址或令牌无效；公网必须 HTTPS";return};do{try store.save(c);message="同步设置已保存"}catch{message=error.localizedDescription}}
    private func run(push:Bool){Task{let result=push ? await SyncClient(repository:model.repository,settings:store).push():await SyncClient(repository:model.repository,settings:store).pull();await MainActor.run{switch result{case .success(_,let value):message=value;case .failure(let value):message=value}}}}
}
