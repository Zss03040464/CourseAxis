import SwiftUI

struct TermManagementView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selectedID: String?
    @State private var name = ""
    @State private var providerID = ""
    @State private var school = ""
    @State private var campus = ""
    @State private var academicYear = ""
    @State private var semester = AcademicSemester.first
    @State private var semesterCode = ""
    @State private var firstMonday = ""
    @State private var totalWeeks = "20"
    @State private var sections: [SectionTime] = []
    @State private var deleteCandidate: Term?

    var body: some View {
        HStack(spacing:0) {
            VStack(spacing:0) {
                HStack { Text("学期").font(.title2.bold()); Spacer(); Button(action:beginNew) { Image(systemName:"plus") }.accessibilityLabel("新建学期") }.padding()
                List(selection:$selectedID) { ForEach(model.document.terms) { term in VStack(alignment:.leading,spacing:3) { HStack { Text(term.name).font(.headline); if model.document.settings.activeTermId == term.id { StatusBadge(text:"当前",color:.green) } }; Text("\(term.school.isEmpty ? "手工学期" : term.school) · \(term.firstWeekMonday) · \(term.totalWeeks) 周").font(.caption).foregroundStyle(.secondary) }.tag(term.id).contextMenu { Button("设为当前学期") { model.activateTerm(term) }; Button("删除",role:.destructive) { deleteCandidate=term } } } }
                    .onChange(of:selectedID) { _,id in if let id,let term=model.document.terms.first(where:{$0.id==id}) { load(term) } }
            }.frame(width:280)
            Divider()
            Form {
                Section("学期信息") {
                    TextField("名称",text:$name)
                    Picker("学校教务", selection:$providerID) {
                        Text("手工学期（不可教务导入）").tag("")
                        ForEach(model.academicProviders,id:\.id) { provider in
                            Text(AcademicProviderDisplayPolicy.labels(for:model.academicProviders)[provider.id] ?? provider.schoolName).tag(provider.id)
                        }
                    }
                    .disabled(selectedID != nil)
                    .onChange(of:providerID) { _,value in applyProvider(value) }
                    if providerID.isEmpty { TextField("学校",text:$school) }
                    HStack {
                        TextField("校区",text:$campus)
                        TextField("学年，例如 2026",text:$academicYear)
                    }
                    if let provider=selectedProvider {
                        Picker("学期",selection:$semester) {
                            ForEach(AcademicProviderSemesterPolicy.options(for:provider)) { option in
                                Text(option.label).tag(option)
                            }
                        }
                        .onChange(of:semester) { _,value in semesterCode=AcademicProviderSemesterPolicy.code(for:value,provider:provider) ?? "" }
                    } else {
                        TextField("学期代码",text:$semesterCode)
                    }
                    HStack {
                        DatePicker("第一周日期",selection:firstWeekBinding,displayedComponents:.date)
                            .environment(\.calendar,LocalValue.calendar)
                            .environment(\.timeZone,LocalValue.calendar.timeZone)
                        Text("保存为周一：\(firstMonday.isEmpty ? "未设置" : firstMonday)").font(.caption).foregroundStyle(.secondary)
                        TextField("总周数",text:$totalWeeks).frame(width:90)
                    }
                }
                Section("真实节次作息") {
                    ForEach(Array(sections.indices),id:\.self) { index in
                        HStack {
                            Text("第 \(sections[index].section) 节").frame(width:70,alignment:.leading)
                            TextField("开始",text:sectionBinding(index,\.start)).frame(width:100)
                            Text("—")
                            TextField("结束",text:sectionBinding(index,\.end)).frame(width:100)
                            Spacer()
                            Button(role:.destructive) { sections.remove(at:index) } label:{ Image(systemName:"minus.circle") }.buttonStyle(.plain).accessibilityLabel("删除第 \(sections[index].section) 节")
                        }
                    }
                    Button { let next=(sections.map(\.section).max() ?? 0)+1;sections.append(SectionTime(section:next,start:"08:00",end:"08:40")) } label:{ Label("添加节次",systemImage:"plus.circle") }
                }
                Section { HStack { Button("保存学期",action:save).buttonStyle(.borderedProminent); if let id=selectedID,let term=model.document.terms.first(where:{$0.id==id}) { Button("设为当前学期") { model.activateTerm(term) }; Spacer(); Button("删除学期",role:.destructive) { deleteCandidate=term } } } }
            }.formStyle(.grouped)
        }
        .onAppear { if let term=model.document.activeTerm { selectedID=term.id;load(term) } else { beginNew() } }
        .alert("删除学期及其全部数据？",isPresented:Binding(get:{deleteCandidate != nil},set:{if !$0{deleteCandidate=nil}})) { Button("取消",role:.cancel){};Button("删除学期",role:.destructive){if let deleteCandidate{model.deleteTerm(deleteCandidate);self.deleteCandidate=nil;beginNew()}} } message:{Text("该学期的课程、考试、调课和作息将一并删除，并同步到其他设备。")}
    }

    private var firstWeekBinding: Binding<Date> { Binding(get:{LocalValue.date(firstMonday) ?? Date()},set:{firstMonday=LocalValue.dateString(monday(containing:$0))}) }
    private func monday(containing date:Date)->Date { let day=LocalValue.calendar.startOfDay(for:date);let weekday=LocalValue.calendar.component(.weekday,from:day);let mondayOffset=weekday==1 ? -6 : 2-weekday;return LocalValue.calendar.date(byAdding:.day,value:mondayOffset,to:day) ?? day }
    private func sectionBinding(_ index:Int,_ keyPath:WritableKeyPath<SectionTime,String>)->Binding<String>{Binding(get:{sections[index][keyPath:keyPath]},set:{sections[index][keyPath:keyPath]=$0})}
    private var selectedProvider:AcademicProvider? { model.academicProviders.first{$0.id==providerID} }
    private func beginNew(){selectedID=nil;name="新学期";let requested=model.document.settings.selectedAcademicProviderId;providerID=model.academicProviders.contains(where:{$0.id==requested}) ? requested:"";school="";campus="";academicYear="";semester = .first;semesterCode="";firstMonday=LocalValue.dateString(monday(containing:Date()));totalWeeks="20";sections=[];applyProvider(providerID)}
    private func load(_ term:Term){name=term.name;providerID=term.providerId ?? "";school=term.school;campus=term.campus;academicYear=term.academicYear;semesterCode=term.semesterCode;if let provider=selectedProvider{semester=AcademicProviderSemesterPolicy.semester(for:term.semesterCode,provider:provider) ?? AcademicProviderSemesterPolicy.options(for:provider).first ?? .first};firstMonday=term.firstWeekMonday;totalWeeks=String(term.totalWeeks);sections=model.document.timeRule(for:term.id)?.sections.sorted{$0.section<$1.section} ?? []}
    private func applyProvider(_ id:String){guard let provider=model.academicProviders.first(where:{$0.id==id})else{if selectedID==nil{school="";semesterCode=""};return};school=provider.schoolName;let options=AcademicProviderSemesterPolicy.options(for:provider);if !options.contains(semester){semester=options.first ?? .first};semesterCode=AcademicProviderSemesterPolicy.code(for:semester,provider:provider) ?? "";if campus.isEmpty,provider.id==ProviderTermIdentity.defaultProviderID{campus="温泉校区"}}
    private func save(){guard let weeks=Int(totalWeeks),(1...40).contains(weeks),LocalValue.date(firstMonday) != nil else{model.errorMessage="学期日期或周数无效";return};let provider=selectedProvider;let resolvedSchool=(provider?.schoolName ?? school).trimmingCharacters(in:.whitespacesAndNewlines);let resolvedCampus=campus.trimmingCharacters(in:.whitespacesAndNewlines);let resolvedYear=academicYear.trimmingCharacters(in:.whitespacesAndNewlines);let resolvedCode=(provider.flatMap{AcademicProviderSemesterPolicy.code(for:semester,provider:$0)} ?? semesterCode).trimmingCharacters(in:.whitespacesAndNewlines);guard !resolvedSchool.isEmpty,!resolvedYear.isEmpty,!resolvedCode.isEmpty else{model.errorMessage="学校、学年和学期不能为空";return};let provisional=Term(id:selectedID ?? "",name:name.isEmpty ? "未命名学期":name,providerId:provider?.id,school:resolvedSchool,campus:resolvedCampus,academicYear:resolvedYear,semesterCode:resolvedCode,firstWeekMonday:firstMonday,totalWeeks:weeks);var term=provisional;term.id=selectedID ?? ProviderTermIdentity.canonicalID(providerId:provider?.id,school:resolvedSchool,campus:resolvedCampus,academicYear:resolvedYear,semesterCode:resolvedCode);let validSections=sections.filter{guard let a=LocalValue.minutes($0.start),let b=LocalValue.minutes($0.end)else{return false};return b>a};let rule=validSections.isEmpty ? nil:TimeRule(id:StableID.make("rule",term.id,validSections.map{"\($0.section):\($0.start)-\($0.end)"}.joined(separator:";")),termId:term.id,school:term.school,campus:term.campus,source:.USER,sections:validSections);model.updateTerm(term,rule:rule);selectedID=term.id}
}
