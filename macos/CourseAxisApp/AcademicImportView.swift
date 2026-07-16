import SwiftUI

struct AcademicImportView: View {
    @EnvironmentObject private var model: AppModel
    @State private var requestToken = 0
    @State private var busy = false
    @State private var preview: AcademicImportBundle?
    @State private var error: String?
    @State private var pageState: AcademicPageState = .loading
    @AppStorage("CourseAxis.lastImportSummary") private var lastImportSummary = "尚未完成导入"

    private var selection: AcademicProviderSelection {
        AcademicProviderSelectionPolicy.resolve(
            providers: model.academicProviders,
            requestedProviderID: model.document.settings.selectedAcademicProviderId,
            terms: model.document.terms,
            activeTermID: model.document.settings.activeTermId
        )
    }

    private var providerLabels: [String: String] {
        AcademicProviderDisplayPolicy.labels(for: model.academicProviders)
    }

    private var pageReady: Bool {
        if case .ready = pageState { return true }
        return false
    }

    var body: some View {
        let provider = selection.provider
        let term = selection.term
        VStack(spacing: 0) {
            PageHeader(
                eyebrow: "学校教务",
                title: "教务导入",
                subtitle: "会话按学校隔离，课程、考试与真实作息一次读取",
                icon: "square.and.arrow.down.fill"
            ) {
                if busy { ProgressView().controlSize(.small) }
            }
            SurfacePanel {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 12) {
                        Picker(
                            "学校教务",
                            selection: Binding(
                                get: { provider?.id ?? "" },
                                set: selectProvider
                            )
                        ) {
                            ForEach(model.academicProviders, id: \.id) { item in
                                Text(providerLabels[item.id] ?? item.schoolName).tag(item.id)
                            }
                        }
                        .labelsHidden()
                        .frame(width: 220)
                        .disabled(model.academicProviders.count < 2)

                        Divider().frame(height: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(term?.name ?? "请先在设置中创建该学校学期")
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                            Text(term.map { "\($0.campus.isEmpty ? "未设置校区" : $0.campus) · \($0.firstWeekMonday) 起 · \($0.totalWeeks) 周" } ?? "导入只使用当前学校的活动学期")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 8)
                        Button {
                            busy = true
                            requestToken += 1
                        } label: {
                            Label(busy ? "读取中…" : "读取课程、考试与真实作息", systemImage: "arrow.down.doc.fill")
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(busy || provider == nil || term == nil || !pageReady)
                    }
                    HStack(spacing: 8) {
                        pageStateLabel
                        Divider().frame(height: 16)
                        Text("最近：\(lastImportSummary)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, CourseAxisDesign.pagePadding)
            .padding(.bottom, 10)

            if let provider, let term {
                ZStack {
                    AcademicWebView(
                        provider: provider,
                        requestToken: requestToken,
                        academicYear: term.academicYear,
                        semesterCode: term.semesterCode,
                        onPageState: { pageState = $0 },
                        onResult: { handle($0, provider: provider, term: term) },
                        onError: {
                            busy = false
                            error = $0
                        }
                    )
                    .id(provider.id)
                    if case .loading = pageState {
                        ProgressView("正在载入\(provider.schoolName)教务页面…")
                            .padding(18)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, CourseAxisDesign.pagePadding)
                .padding(.bottom, CourseAxisDesign.pagePadding)
            } else {
                ContentUnavailableView(
                    "教务导入暂不可用",
                    systemImage: "building.2.crop.circle",
                    description: Text(model.academicProviderError ?? "请先在设置中创建当前学校的学期")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(CourseAxisDesign.pageBackground)
        .navigationTitle("教务导入")
        .onAppear(perform: normalizeProviderSelection)
        .onChange(of: provider?.id) {
            requestToken = 0
            busy = false
            preview = nil
            pageState = .loading
        }
        .alert(
            "导入失败",
            isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })
        ) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(error ?? "")
        }
        .sheet(
            item: Binding(
                get: {
                    preview.map {
                        ImportPreviewItem(
                            bundle: $0,
                            plan: SchoolImportPlan.make(document: model.document, bundle: $0)
                        )
                    }
                },
                set: { if $0 == nil { preview = nil } }
            )
        ) { item in
            ImportPreviewView(
                bundle: item.bundle,
                plan: item.plan,
                onCancel: { preview = nil },
                onCommit: {
                    model.applyImport(item.bundle)
                    preview = nil
                }
            )
        }
    }

    private func selectProvider(_ providerID: String) {
        guard model.academicProviders.contains(where: { $0.id == providerID }) else { return }
        var settings = model.document.settings
        settings.selectedAcademicProviderId = providerID
        let providerTerm = AcademicProviderSelectionPolicy.resolve(
            providers: model.academicProviders,
            requestedProviderID: providerID,
            terms: model.document.terms,
            activeTermID: settings.activeTermId
        ).term
        if let providerTerm { settings.activeTermId = providerTerm.id }
        model.updateSettings(settings)
    }

    private func normalizeProviderSelection() {
        guard let provider = selection.provider else { return }
        if model.document.settings.selectedAcademicProviderId != provider.id {
            selectProvider(provider.id)
        }
    }

    private func handle(_ raw: String, provider: AcademicProvider, term: Term) {
        busy = false
        do {
            let options = AcademicImportOptions(
                provider: provider,
                academicYear: term.academicYear,
                semesterCode: term.semesterCode,
                termName: term.name,
                firstWeekMonday: term.firstWeekMonday,
                totalWeeks: term.totalWeeks,
                campus: term.campus
            )
            let result = try AcademicImportAdapterRegistry.adapter(for: provider).parse(raw, options: options)
            preview = result
            lastImportSummary = "\(result.term.name) · \(result.courseCount) 门课程 · \(result.examCount) 场考试"
        } catch {
            self.error = error.localizedDescription
        }
    }

    @ViewBuilder
    private var pageStateLabel: some View {
        switch pageState {
        case .loading:
            Label("页面载入中", systemImage: "hourglass").foregroundStyle(.secondary)
        case .ready(let title):
            Label(title.isEmpty ? "教务已就绪" : title, systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
        case .failed(let message):
            Label("载入失败：\(message)", systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
        }
    }
}

private struct ImportPreviewView: View {
    let bundle: AcademicImportBundle
    let plan: SchoolImportPlan
    let onCancel: () -> Void
    let onCommit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("导入完整性验收").font(.title2.bold())
            Text("\(bundle.term.name) · 第一周周一 \(bundle.term.firstWeekMonday) · \(bundle.term.totalWeeks) 周")
            SourceStatusRow(title: "课程源", status: bundle.courseSource, detail: "\(bundle.courseCount) 门；星期、周次、地点、教师已解析")
            SourceStatusRow(title: "真实作息", status: bundle.sectionSource, detail: "\(bundle.timeRule?.sections.count ?? 0) 节；课程待确认 \(bundle.courseUnresolvedCount) 门")
            SourceStatusRow(title: "考试源", status: bundle.examSource, detail: "\(bundle.examCount) 项；使用独立考试接口")
            SurfacePanel {
                VStack(alignment: .leading, spacing: 10) {
                    Text("写入差异").font(.headline)
                    HStack(spacing: 8) {
                        ImportCountBadge(title: "新增", count: plan.inserted.count, color: .green)
                        ImportCountBadge(title: "更新", count: plan.updated.count, color: .blue)
                        ImportCountBadge(title: "未变化", count: plan.unchanged.count, color: .secondary)
                        ImportCountBadge(title: "删除", count: plan.removed.count, color: .red)
                        ImportCountBadge(title: "用户保护", count: plan.protected.count, color: .orange)
                    }
                    if !plan.preservedExams.isEmpty {
                        Label("考试接口本次返回 0 项，已保留本地 \(plan.preservedExams.count) 项考试，防止临时空结果误删。", systemImage: "shield.checkered")
                            .font(.caption).foregroundStyle(.orange)
                    }
                }
            }
            if !bundle.canCommit {
                Label("本次结果已阻止写入：课程源必须成功，且每门课都要有真实起止时间。", systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
            } else {
                Label(
                    bundle.examSource.ok ? "可写入：课程与考试源均已读取。" : "可写入课程：考试源暂时不可用，已有考试会保留。",
                    systemImage: bundle.examSource.ok ? "checkmark.seal.fill" : "exclamationmark.shield.fill"
                )
                .foregroundStyle(bundle.examSource.ok ? .green : .orange)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(bundle.diagnostics.suffix(14).enumerated()), id: \.offset) {
                        Text("• \($0.element)").font(.caption)
                    }
                }
            }
            HStack {
                Spacer()
                Button("取消", action: onCancel)
                Button("通过验收并写入", action: onCommit)
                    .keyboardShortcut(.defaultAction)
                    .disabled(!bundle.canCommit)
            }
        }
        .padding(24)
        .frame(width: 650, height: 520)
    }
}

private struct ImportCountBadge: View {
    let title: String
    let count: Int
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text("\(count)").font(.title3.bold())
            Text(title).font(.caption2)
        }
        .foregroundStyle(color)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(color.opacity(0.09), in: RoundedRectangle(cornerRadius: 9))
    }
}

private struct SourceStatusRow: View {
    let title: String
    let status: AcademicSourceStatus
    let detail: String

    var body: some View {
        HStack(alignment: .top) {
            Image(systemName: status.ok ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundStyle(status.ok ? .green : .red)
            VStack(alignment: .leading) {
                Text(title).font(.headline)
                Text(detail).font(.caption).foregroundStyle(.secondary)
                if !status.endpoint.isEmpty {
                    Text("来源：\(status.endpoint)").font(.caption2).foregroundStyle(.tertiary)
                }
            }
            Spacer()
        }
        .padding(10)
        .background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct ImportPreviewItem: Identifiable {
    let id = UUID()
    let bundle: AcademicImportBundle
    let plan: SchoolImportPlan
}
