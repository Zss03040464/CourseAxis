import Foundation

enum AppGroupMigrationResult: Equatable {
    case noLegacyDocument
    case targetAlreadyExists
    case migrated(backupURL: URL)
}

enum AppGroupMigration {
    static func migrate(
        legacyDirectory: URL,
        groupDirectory: URL,
        fileManager: FileManager = .default,
        now: Date = Date()
    ) throws -> AppGroupMigrationResult {
        let fileName = "schedule.document.json"
        let legacyURL = legacyDirectory.appendingPathComponent(fileName)
        let targetURL = groupDirectory.appendingPathComponent(fileName)
        guard !fileManager.fileExists(atPath: targetURL.path) else {
            return .targetAlreadyExists
        }
        guard fileManager.fileExists(atPath: legacyURL.path) else {
            return .noLegacyDocument
        }

        let originalData = try Data(contentsOf: legacyURL)
        let decoded = try JSONDecoder().decode(ScheduleDocument.self, from: originalData)
        try ScheduleDocumentValidator.validate(DocumentMigration.migrate(decoded).document)
        try fileManager.createDirectory(at: groupDirectory, withIntermediateDirectories: true)

        let probeURL = groupDirectory.appendingPathComponent(".courseaxis-migration-write-probe")
        let migrationBackupURL = groupDirectory.appendingPathComponent("schedule.document.pre-migration.json")
        var readOnlyBackupURL: URL?
        do {
            try fileManager.copyItem(at: legacyURL, to: targetURL)
            _ = try DocumentFileStore(directoryURL: groupDirectory).load()
            let migratedData = try Data(contentsOf: targetURL)
            try migratedData.write(to: probeURL, options: .atomic)
            guard try Data(contentsOf: probeURL) == migratedData else {
                throw CocoaError(.fileWriteUnknown)
            }

            let backupDirectory = groupDirectory.appendingPathComponent("Migration Backups", isDirectory: true)
            try fileManager.createDirectory(at: backupDirectory, withIntermediateDirectories: true)
            let timestamp = Int(now.timeIntervalSince1970)
            let backupURL = backupDirectory.appendingPathComponent(
                "schedule.document.legacy-\(timestamp).json"
            )
            try originalData.write(to: backupURL, options: .atomic)
            try fileManager.setAttributes([.posixPermissions: 0o444], ofItemAtPath: backupURL.path)
            readOnlyBackupURL = backupURL

            try fileManager.removeItem(at: probeURL)
            try fileManager.removeItem(at: legacyURL)
            return .migrated(backupURL: backupURL)
        } catch {
            try? fileManager.removeItem(at: probeURL)
            try? fileManager.removeItem(at: targetURL)
            try? fileManager.removeItem(at: migrationBackupURL)
            if let readOnlyBackupURL { try? fileManager.removeItem(at: readOnlyBackupURL) }
            throw error
        }
    }
}
