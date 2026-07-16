import AppKit
import Foundation
import SwiftUI
import WidgetKit

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var document: ScheduleDocument
    @Published var errorMessage: String?
    @Published var noticeMessage: String?
    let repository: ScheduleRepository
    let academicProviders: [AcademicProvider]
    let academicProviderError: String?
    private let store: DocumentFileStore
    private let notifications=NotificationCoordinator()

    init() {
        let catalog = Result { try AcademicProviderCatalog.load() }
        switch catalog {
        case .success(let providers):
            academicProviders = providers
            academicProviderError = nil
        case .failure(let error):
            academicProviders = []
            academicProviderError = error.localizedDescription
        }
        let setup: (DocumentFileStore, ScheduleRepository, String?) = {
            do {
                let store = try DocumentFileStore.appStore()
                return (store, try ScheduleRepository(store: store), nil)
            } catch {
                let fallback = try! DocumentFileStore(directoryURL: FileManager.default.temporaryDirectory.appendingPathComponent("CourseAxis-Fallback", isDirectory: true))
                return (fallback, try! ScheduleRepository(store: fallback), error.localizedDescription)
            }
        }()
        store = setup.0
        repository = setup.1
        document = setup.1.document
        errorMessage = setup.2
        repository.onChange = { [weak self] value in
            Task { @MainActor in
                self?.document = value
                WidgetCenter.shared.reloadAllTimelines()
            }
        }
    }

    func save(_ event: ScheduleEvent) { perform { try repository.upsertEvent(event) } }
    func delete(_ event: ScheduleEvent) { perform { try repository.deleteEvent(id: event.id) } }
    func delete(_ events: [ScheduleEvent]) { perform { try repository.deleteEvents(ids: Set(events.map(\.id))); noticeMessage = "已删除 \(events.count) 项日程" } }
    var canUndoDestructiveChange:Bool { repository.canUndoDestructiveChange }
    func undoLastDeletion(){perform{try repository.undoLastDestructiveChange();noticeMessage="已恢复上一次删除"}}
    func duplicate(_ event: ScheduleEvent) { perform { _ = try repository.duplicateEvent(id: event.id); noticeMessage = "已创建“\(event.title) 副本”" } }
    func deleteTerm(_ term: Term) { perform { try repository.deleteTerm(id: term.id); noticeMessage = "已删除学期及其关联数据" } }
    func activateTerm(_ term: Term) { var settings=document.settings;settings.activeTermId=term.id;if let providerID=term.providerId{settings.selectedAcademicProviderId=providerID};updateSettings(settings) }
    func applyImport(_ bundle: AcademicImportBundle) { perform { try repository.applySchoolImport(bundle); noticeMessage = "已导入 \(bundle.courseCount) 门课程和 \(bundle.examCount) 项考试" } }
    func updateSettings(_ settings: AppSettings) { perform { try repository.updateSettings(settings) } }
    func updateTerm(_ term: Term, rule: TimeRule?) { perform { try repository.updateTerm(term, rule: rule) } }
    func replaceWithMerged(_ remote: ScheduleDocument) { perform { try repository.replace(MergeEngine.merge(document, remote)) } }
    func scheduleExamReminders(){Task{do{let count=try await notifications.scheduleExamReminders(document:document);noticeMessage="已安排 \(count) 条考试提醒"}catch{errorMessage=error.localizedDescription}}}
    func clearAcademicSession() {
        let selection = AcademicProviderSelectionPolicy.resolve(
            providers: academicProviders,
            requestedProviderID: document.settings.selectedAcademicProviderId,
            terms: document.terms,
            activeTermID: document.settings.activeTermId
        )
        guard let provider = selection.provider else {
            errorMessage = academicProviderError ?? "没有可用的学校教务配置"
            return
        }
        Task {
            do {
                try await AcademicWebSession.clear(provider: provider)
                noticeMessage = "已清除\(provider.schoolName)教务会话与网页缓存"
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func clearAllLocalData() {
        Task {
            do {
                try await AcademicWebSession.clearAll(providers: academicProviders)
                try SyncSettingsStore().clear()
                try repository.clearAllLocalData()
                noticeMessage = "已清除全部本机数据"
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func importJSON() {
        let panel = NSOpenPanel(); panel.allowedContentTypes = [.json]; panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url else { return }
        perform { try repository.replace(store.importData(Data(contentsOf: url))); noticeMessage = "JSON 导入完成" }
    }

    func exportJSON() {
        let panel = NSSavePanel(); panel.allowedContentTypes = [.json]; panel.nameFieldStringValue = "schedule.document.json"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        perform {
            let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
            try encoder.encode(document).write(to: url, options: .atomic)
            noticeMessage = "JSON 已导出"
        }
    }

    private func perform(_ operation: () throws -> Void) {
        do { try operation() } catch { errorMessage = error.localizedDescription }
    }
}
