import Foundation

enum EventCatalogKind: String, CaseIterable { case all, course, exam, custom, adjustment }
enum EventCatalogResolution: String, CaseIterable { case all, resolved, unresolved }

enum EventCatalog {
    static func filter(_ events: [ScheduleEvent], query: String, kind: EventCatalogKind, resolution: EventCatalogResolution, termID: String?) -> [ScheduleEvent] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).localizedLowercase
        return events.filter { event in
            let matchesQuery = needle.isEmpty || [event.title,event.teacher,event.location,event.campus,event.notes].joined(separator:" ").localizedLowercase.contains(needle)
            let matchesKind: Bool = switch kind { case .all:true;case .course:event.kind == .COURSE;case .exam:event.kind == .EXAM;case .custom:event.kind == .CUSTOM;case .adjustment:event.kind == .ADJUSTMENT }
            let matchesResolution: Bool = switch resolution { case .all:true;case .resolved:event.isResolved;case .unresolved:!event.isResolved }
            let matchesTerm = termID == nil || event.termId == termID
            return matchesQuery && matchesKind && matchesResolution && matchesTerm
        }.sorted { lhs,rhs in
            if lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedSame { return lhs.id < rhs.id }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
    }
}
