import Foundation

final class ScheduleRepository {
    private let store: DocumentFileStore
    private(set) var document: ScheduleDocument
    var onChange: ((ScheduleDocument) -> Void)?
    var canUndoDestructiveChange: Bool { store.hasRecoverySnapshot }

    init(store: DocumentFileStore) throws {
        self.store = store
        document = try store.load()
    }

    func replace(_ value: ScheduleDocument) throws {
        try persist(value)
    }

    func upsertEvent(_ event: ScheduleEvent) throws {
        var next = document
        var value = event
        value.updatedAt = LocalValue.isoNow()
        next.events.removeAll { $0.id == value.id }
        next.events.append(value)
        next.tombstones.removeAll { $0.entityType == .EVENT && $0.id == value.id }
        try persist(next)
    }

    func deleteEvent(id: String) throws {
        try deleteEvents(ids: [id])
    }

    func deleteEvents(ids: Set<String>) throws {
        guard !ids.isEmpty else { return }
        var next = document
        let existingIDs = Set(next.events.lazy.map(\.id).filter(ids.contains))
        guard !existingIDs.isEmpty else { return }
        try store.saveRecoverySnapshot(document)
        let deletedAt = LocalValue.isoNow()
        next.events.removeAll { existingIDs.contains($0.id) }
        next.tombstones.removeAll { $0.entityType == .EVENT && existingIDs.contains($0.id) }
        next.tombstones.append(contentsOf: existingIDs.sorted().map { Tombstone(id: $0, entityType: .EVENT, deletedAt: deletedAt) })
        try persist(next)
    }

    @discardableResult func duplicateEvent(id: String) throws -> ScheduleEvent {
        guard var copy = document.events.first(where: { $0.id == id }) else {
            throw NSError(domain: "CourseAxis.Repository", code: 404, userInfo: [NSLocalizedDescriptionKey: "找不到要复制的事件"])
        }
        copy.id = StableID.make("user", UUID().uuidString)
        copy.externalId = nil
        copy.title += " 副本"
        copy.timeSource = .USER
        copy.updatedAt = LocalValue.isoNow()
        try upsertEvent(copy)
        return copy
    }

    func deleteTerm(id: String) throws {
        guard document.terms.contains(where: { $0.id == id }) else { return }
        try store.saveRecoverySnapshot(document)
        var next = document
        let rules = next.timeRules.filter { $0.termId == id }
        let events = next.events.filter { $0.termId == id }
        let deletedAt = LocalValue.isoNow()
        next.terms.removeAll { $0.id == id }
        next.timeRules.removeAll { $0.termId == id }
        next.events.removeAll { $0.termId == id }
        next.tombstones.removeAll { tombstone in
            (tombstone.entityType == .TERM && tombstone.id == id) ||
            (tombstone.entityType == .TIME_RULE && rules.contains { $0.id == tombstone.id }) ||
            (tombstone.entityType == .EVENT && events.contains { $0.id == tombstone.id })
        }
        next.tombstones.append(Tombstone(id: id, entityType: .TERM, deletedAt: deletedAt))
        next.tombstones.append(contentsOf: rules.map { Tombstone(id: $0.id, entityType: .TIME_RULE, deletedAt: deletedAt) })
        next.tombstones.append(contentsOf: events.map { Tombstone(id: $0.id, entityType: .EVENT, deletedAt: deletedAt) })
        if next.settings.activeTermId == id {
            let replacement = next.terms.max { LocalValue.instant($0.updatedAt) < LocalValue.instant($1.updatedAt) }
            next.settings.activeTermId = replacement?.id
            if let providerID = replacement?.providerId {
                next.settings.selectedAcademicProviderId = providerID
            }
        }
        try persist(next)
    }

    func updateTerm(_ term: Term, rule: TimeRule?) throws {
        var next = document
        var updatedTerm = term
        updatedTerm.updatedAt = LocalValue.isoNow()
        next.terms.removeAll { $0.id == updatedTerm.id }
        next.terms.append(updatedTerm)
        next.timeRules.removeAll { $0.termId == updatedTerm.id }
        if var rule { rule.updatedAt = LocalValue.isoNow(); next.timeRules.append(rule) }
        next.settings.activeTermId = updatedTerm.id
        if let providerID = updatedTerm.providerId {
            next.settings.selectedAcademicProviderId = providerID
        }
        next.events = next.events.map { event in
            guard event.termId == updatedTerm.id, event.recurrence == .WEEKLY,
                  ![TimeSource.USER, .EXPLICIT].contains(event.timeSource),
                  let first = event.startSection, let last = event.endSection else { return event }
            var updated = event
            if let rule, let resolved = TimeResolver.resolve(Array(first...last), rule: rule) {
                updated.startTime = LocalValue.timeString(resolved.0)
                updated.endTime = LocalValue.timeString(resolved.1)
                updated.timeRuleId = rule.id
                updated.timeSource = .SECTION_RULE
                updated.resolutionStatus = .RESOLVED
            } else {
                updated.startTime = nil
                updated.endTime = nil
                updated.timeRuleId = rule?.id
                updated.timeSource = .UNRESOLVED
                updated.resolutionStatus = .UNRESOLVED
            }
            updated.updatedAt = LocalValue.isoNow()
            return updated
        }
        try persist(next)
    }

    func applySchoolImport(_ bundle: AcademicImportBundle) throws {
        guard bundle.canCommit else {
            throw NSError(domain: "CourseAxis.Import", code: 2, userInfo: [NSLocalizedDescriptionKey: "导入未通过完整性验收：请确认学期、课程真实时间和考试源后重试"])
        }
        var importedTerm = bundle.term
        if let existing = document.terms.first(where: { DocumentMigration.naturalKey($0) == DocumentMigration.naturalKey(bundle.term) }) {
            importedTerm.id = existing.id
            importedTerm.name = existing.name
            importedTerm.firstWeekMonday = existing.firstWeekMonday
            importedTerm.totalWeeks = existing.totalWeeks
        }
        let plan = SchoolImportPlan.make(document: document, bundle: bundle)
        let liveIDs = Set((plan.unchanged + plan.protected + plan.preservedExams).map(\.id))
        let hasStaleLiveTombstone = document.tombstones.contains { $0.entityType == .EVENT && liveIDs.contains($0.id) }
        if plan.inserted.isEmpty, plan.updated.isEmpty, plan.removed.isEmpty,
           sameTermIgnoringTimestamp(document.terms.first { $0.id == importedTerm.id }, importedTerm),
           sameRuleIgnoringTimestamp(document.timeRule(for: bundle.term.id), bundle.timeRule),
           document.settings.activeTermId == bundle.term.id,
           !hasStaleLiveTombstone {
            return
        }
        var next = document
        next.events = plan.resultEvents
        next.terms.removeAll { $0.id == importedTerm.id }
        next.terms.append(importedTerm)
        next.timeRules.removeAll { $0.termId == bundle.term.id }
        if let rule = bundle.timeRule { next.timeRules.append(rule) }
        next.settings.activeTermId = importedTerm.id
        if let providerID = importedTerm.providerId {
            next.settings.selectedAcademicProviderId = providerID
        }
        let deletionTime = LocalValue.isoNow()
        for event in plan.removed {
            next.tombstones.removeAll { $0.entityType == .EVENT && $0.id == event.id }
            next.tombstones.append(Tombstone(id: event.id, entityType: .EVENT, deletedAt: deletionTime))
        }
        let liveImportedIDs = Set((plan.inserted + plan.updated + plan.unchanged + plan.protected + plan.preservedExams).map(\.id))
        next.tombstones.removeAll { $0.entityType == .EVENT && liveImportedIDs.contains($0.id) }
        try persist(next)
    }

    private func sameTermIgnoringTimestamp(_ lhs: Term?, _ rhs: Term) -> Bool {
        guard var lhs else { return false }
        var rhs = rhs
        lhs.updatedAt = ""; rhs.updatedAt = ""
        return lhs == rhs
    }

    private func sameRuleIgnoringTimestamp(_ lhs: TimeRule?, _ rhs: TimeRule?) -> Bool {
        guard var lhs, var rhs else { return lhs == nil && rhs == nil }
        lhs.updatedAt = ""; rhs.updatedAt = ""
        return lhs == rhs
    }

    func updateSettings(_ settings: AppSettings) throws {
        guard document.settings != settings else { return }
        var next = document
        next.settings = settings
        try persist(next)
    }

    func undoLastDestructiveChange() throws {
        guard store.hasRecoverySnapshot else { return }
        let recovered=try store.loadRecoverySnapshot()
        try persist(recovered)
        try store.clearRecoverySnapshot()
    }

    func clearAllLocalData() throws {
        try store.clearAllLocalData()
        document = .empty
        onChange?(document)
    }

    private func persist(_ value: ScheduleDocument) throws {
        var next = value
        next.updatedAt = LocalValue.isoNow()
        try store.save(next)
        document = next
        onChange?(next)
    }
}
