import Foundation

enum DocumentStoreError: LocalizedError {
    case appGroupUnavailable(String)
    case invalidDocument(String)

    var errorDescription: String? {
        switch self {
        case .appGroupUnavailable(let group): return "无法访问 App Group：\(group)"
        case .invalidDocument(let reason): return "课程表数据无效：\(reason)"
        }
    }
}

struct ScheduleDocumentValidator {
    static func validate(_ document: ScheduleDocument) throws {
        guard document.schemaVersion == 1 else { throw DocumentStoreError.invalidDocument("不支持的 schemaVersion") }
        guard !document.documentId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw DocumentStoreError.invalidDocument("documentId 为空") }
        guard Set(document.terms.map(\.id)).count == document.terms.count else { throw DocumentStoreError.invalidDocument("学期 ID 重复") }
        guard Set(document.timeRules.map(\.id)).count == document.timeRules.count else { throw DocumentStoreError.invalidDocument("作息 ID 重复") }
        guard Set(document.events.map(\.id)).count == document.events.count else { throw DocumentStoreError.invalidDocument("事件 ID 重复") }
        guard document.terms.allSatisfy({ (1...40).contains($0.totalWeeks) && LocalValue.date($0.firstWeekMonday) != nil }) else { throw DocumentStoreError.invalidDocument("学期日期或周数无效") }
        let naturalKeys=document.terms.filter{!$0.academicYear.isEmpty && !$0.semesterCode.isEmpty}.map(DocumentMigration.naturalKey)
        guard Set(naturalKeys).count == naturalKeys.count else { throw DocumentStoreError.invalidDocument("存在语义重复学期") }
        let termIDs=Set(document.terms.map(\.id))
        guard document.timeRules.allSatisfy({termIDs.contains($0.termId)}) else { throw DocumentStoreError.invalidDocument("作息引用了不存在的学期") }
        if let active=document.settings.activeTermId { guard termIDs.contains(active) else { throw DocumentStoreError.invalidDocument("当前学期引用无效") } }
        guard let dayStart=LocalValue.minutes(document.settings.dayStart),let dayEnd=LocalValue.minutes(document.settings.dayEnd),dayEnd>dayStart else { throw DocumentStoreError.invalidDocument("时间轴起止时间无效") }
        guard (1...5).contains(document.settings.widgetUpcomingCount) else { throw DocumentStoreError.invalidDocument("桌面未来课程数量必须为 1–5") }
        for event in document.events {
            guard !event.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw DocumentStoreError.invalidDocument("事件名称为空") }
            if event.resolutionStatus == .RESOLVED {
                guard let start = LocalValue.minutes(event.startTime), let end = LocalValue.minutes(event.endTime), end > start else { throw DocumentStoreError.invalidDocument("已解析事件的时间无效") }
            }
            if event.recurrence == .SINGLE, event.date.flatMap(LocalValue.date) == nil { throw DocumentStoreError.invalidDocument("单次事件缺少日期") }
            if event.recurrence == .WEEKLY, event.termId == nil || event.weekday == nil || event.weeks.isEmpty { throw DocumentStoreError.invalidDocument("周期事件缺少学期、星期或周次") }
            if let termID=event.termId,!termIDs.contains(termID) { throw DocumentStoreError.invalidDocument("事件引用了不存在的学期") }
        }
    }
}

final class DocumentFileStore {
    static let appGroupIdentifier = "group.dev.shusenzheng64.courseaxis"
    let directoryURL: URL
    let documentURL: URL
    let backupURL: URL
    let migrationBackupURL: URL
    let recoveryURL: URL
    private let legacyDirectoryURL: URL?
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    convenience init(appGroupIdentifier: String = DocumentFileStore.appGroupIdentifier) throws {
        guard let directory = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            throw DocumentStoreError.appGroupUnavailable(appGroupIdentifier)
        }
        try self.init(directoryURL: directory)
    }

    static func appStore(
        fileManager: FileManager = .default,
        legacyDirectory: URL? = nil
    ) throws -> DocumentFileStore {
        guard let groupDirectory = fileManager.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        ) else {
            throw DocumentStoreError.appGroupUnavailable(appGroupIdentifier)
        }
        let sourceDirectory: URL
        if let legacyDirectory {
            sourceDirectory = legacyDirectory
        } else {
            sourceDirectory = try fileManager
                .url(
                    for: .applicationSupportDirectory,
                    in: .userDomainMask,
                    appropriateFor: nil,
                    create: true
                )
                .appendingPathComponent("CourseAxis", isDirectory: true)
        }
        _ = try AppGroupMigration.migrate(
            legacyDirectory: sourceDirectory,
            groupDirectory: groupDirectory,
            fileManager: fileManager
        )
        return try DocumentFileStore(
            directoryURL: groupDirectory,
            legacyDirectoryURL: sourceDirectory
        )
    }

    init(directoryURL: URL, legacyDirectoryURL: URL? = nil) throws {
        self.directoryURL = directoryURL
        self.legacyDirectoryURL = legacyDirectoryURL
        documentURL = directoryURL.appendingPathComponent("schedule.document.json")
        backupURL = directoryURL.appendingPathComponent("schedule.document.backup.json")
        migrationBackupURL = directoryURL.appendingPathComponent("schedule.document.pre-migration.json")
        recoveryURL = directoryURL.appendingPathComponent("schedule.document.recovery.json")
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        decoder = JSONDecoder()
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
    }

    func load() throws -> ScheduleDocument {
        guard FileManager.default.fileExists(atPath: documentURL.path) else { return .empty }
        do {
            let originalData = try Data(contentsOf: documentURL)
            let document = try decoder.decode(ScheduleDocument.self, from: originalData)
            let migration = DocumentMigration.migrate(document)
            try ScheduleDocumentValidator.validate(migration.document)
            if migration.changed {
                try originalData.write(to: migrationBackupURL, options: .atomic)
                try encoder.encode(migration.document).write(to: documentURL, options: .atomic)
            }
            return migration.document
        } catch {
            throw DocumentStoreError.invalidDocument(error.localizedDescription)
        }
    }

    func save(_ document: ScheduleDocument) throws {
        try ScheduleDocumentValidator.validate(document)
        let data = try encoder.encode(document)
        if FileManager.default.fileExists(atPath: documentURL.path) {
            try? FileManager.default.removeItem(at: backupURL)
            try FileManager.default.copyItem(at: documentURL, to: backupURL)
        }
        try data.write(to: documentURL, options: .atomic)
    }

    func importData(_ data: Data) throws -> ScheduleDocument {
        let document = DocumentMigration.migrate(try decoder.decode(ScheduleDocument.self, from: data)).document
        try ScheduleDocumentValidator.validate(document)
        return document
    }

    func exportData() throws -> Data { try encoder.encode(load()) }

    var hasRecoverySnapshot: Bool { FileManager.default.fileExists(atPath:recoveryURL.path) }
    func saveRecoverySnapshot(_ document:ScheduleDocument)throws { try ScheduleDocumentValidator.validate(document);try encoder.encode(document).write(to:recoveryURL,options:.atomic) }
    func loadRecoverySnapshot()throws->ScheduleDocument { let document=DocumentMigration.migrate(try decoder.decode(ScheduleDocument.self,from:Data(contentsOf:recoveryURL))).document;try ScheduleDocumentValidator.validate(document);return document }
    func clearRecoverySnapshot()throws { if hasRecoverySnapshot { try FileManager.default.removeItem(at:recoveryURL) } }

    func clearAllLocalData() throws {
        let fileManager = FileManager.default
        let urls = [documentURL, backupURL, migrationBackupURL, recoveryURL]
        for url in urls where fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        let migrationBackups = directoryURL.appendingPathComponent("Migration Backups", isDirectory: true)
        if fileManager.fileExists(atPath: migrationBackups.path) {
            try fileManager.removeItem(at: migrationBackups)
        }
        if let legacyDirectoryURL,
           legacyDirectoryURL.standardizedFileURL != directoryURL.standardizedFileURL,
           fileManager.fileExists(atPath: legacyDirectoryURL.path) {
            try fileManager.removeItem(at: legacyDirectoryURL)
        }
    }
}
