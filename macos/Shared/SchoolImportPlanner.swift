import Foundation

struct SchoolImportPlan {
    var inserted: [ScheduleEvent]
    var updated: [ScheduleEvent]
    var unchanged: [ScheduleEvent]
    var removed: [ScheduleEvent]
    var protected: [ScheduleEvent]
    var preservedExams: [ScheduleEvent]
    var resultEvents: [ScheduleEvent]

    static func make(document: ScheduleDocument, bundle: AcademicImportBundle) -> SchoolImportPlan {
        let managedKinds: Set<EventKind> = [.COURSE, .EXAM]
        let existingManaged = document.events.filter { $0.termId == bundle.term.id && managedKinds.contains($0.kind) }
        let incoming = bundle.events.filter { managedKinds.contains($0.kind) }
        let oldByID = Dictionary(uniqueKeysWithValues: existingManaged.map { ($0.id, $0) })
        let incomingIDs = Set(incoming.map(\.id))
        let protectedByID = Dictionary(uniqueKeysWithValues: existingManaged.filter { $0.timeSource == .USER }.map { ($0.id, $0) })

        var inserted: [ScheduleEvent] = []
        var updated: [ScheduleEvent] = []
        var unchanged: [ScheduleEvent] = []
        let protected = protectedByID.values.sorted(by: eventOrder)
        for candidate in incoming.sorted(by: eventOrder) {
            if protectedByID[candidate.id] != nil {
                continue
            } else if let old = oldByID[candidate.id] {
                if semanticallyEqual(old, candidate) { unchanged.append(old) }
                else { updated.append(candidate) }
            } else {
                inserted.append(candidate)
            }
        }

        let oldUnprotected = existingManaged.filter { $0.timeSource != .USER && !incomingIDs.contains($0.id) }
        let existingExams = oldUnprotected.filter { $0.kind == .EXAM }
        let incomingExamCount = incoming.lazy.filter { $0.kind == .EXAM }.count
        let protectEmptyExamSnapshot = (!bundle.examSource.ok || incomingExamCount == 0) && !existingExams.isEmpty
        let preservedExams = protectEmptyExamSnapshot ? existingExams.sorted(by: eventOrder) : []
        let removed = oldUnprotected.filter { event in
            if event.kind == .EXAM && protectEmptyExamSnapshot { return false }
            return true
        }.sorted(by: eventOrder)

        let replacementIDs = Set(updated.map(\.id))
        let removedIDs = Set(removed.map(\.id))
        var result = document.events.filter { !replacementIDs.contains($0.id) && !removedIDs.contains($0.id) }
        result.append(contentsOf: updated)
        result.append(contentsOf: inserted)
        result = Dictionary(grouping: result, by: \.id).compactMap { $0.value.first }.sorted(by: eventOrder)
        return SchoolImportPlan(
            inserted: inserted,
            updated: updated,
            unchanged: unchanged,
            removed: removed,
            protected: protected,
            preservedExams: preservedExams,
            resultEvents: result
        )
    }

    private static func semanticallyEqual(_ lhs: ScheduleEvent, _ rhs: ScheduleEvent) -> Bool {
        var a = lhs; var b = rhs
        a.updatedAt = ""; b.updatedAt = ""
        return a == b
    }

    private static func eventOrder(_ lhs: ScheduleEvent, _ rhs: ScheduleEvent) -> Bool {
        (lhs.kind.rawValue, lhs.title, lhs.id) < (rhs.kind.rawValue, rhs.title, rhs.id)
    }
}
