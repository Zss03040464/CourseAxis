import Foundation

struct DocumentMigrationResult {
    var document: ScheduleDocument
    var changed: Bool
    var mergedTermCount: Int
}

enum ProviderTermIdentity {
    static let defaultProviderID = "nfu-zhengfang"
    static let defaultProviderSchool = "广州南方学院"

    static func inferredProviderID(for term: Term) -> String? {
        if let providerID = term.providerId, !providerID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return normalize(providerID)
        }
        return term.school.trimmingCharacters(in: .whitespacesAndNewlines) == defaultProviderSchool
            ? defaultProviderID
            : nil
    }

    static func naturalKey(
        providerId: String?,
        school: String,
        campus: String,
        academicYear: String,
        semesterCode: String
    ) -> String {
        [
            identityScope(providerId: providerId, school: school),
            normalize(campus),
            normalize(academicYear),
            normalize(semesterCode),
        ].joined(separator: "\u{001F}")
    }

    static func canonicalID(
        providerId: String?,
        school: String,
        campus: String,
        academicYear: String,
        semesterCode: String
    ) -> String {
        StableID.make(
            "term",
            identityScope(providerId: providerId, school: school),
            normalize(campus),
            normalize(academicYear),
            normalize(semesterCode)
        )
    }

    private static func identityScope(providerId: String?, school: String) -> String {
        normalize(providerId.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 } ?? school)
    }

    private static func normalize(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .precomposedStringWithCanonicalMapping
            .lowercased(with: Locale(identifier: "en_US_POSIX"))
    }
}

enum DocumentMigration {
    static func migrate(_ document: ScheduleDocument) -> DocumentMigrationResult {
        let normalizedTerms = document.terms.map { term -> Term in
            var updated = term
            updated.providerId = ProviderTermIdentity.inferredProviderID(for: term)
            return updated
        }
        var idMap: [String: String] = [:]
        var canonicalTerms: [Term] = []

        for group in Dictionary(grouping: normalizedTerms, by: naturalKey).values {
            guard let selected = group.max(by: { LocalValue.instant($0.updatedAt) < LocalValue.instant($1.updatedAt) }) else { continue }
            let canonicalID = canonicalID(for: selected)
            var canonical = selected
            canonical.id = canonicalID
            canonicalTerms.append(canonical)
            for term in group { idMap[term.id] = canonicalID }
        }

        canonicalTerms.sort { ($0.academicYear, $0.semesterCode, $0.id) < ($1.academicYear, $1.semesterCode, $1.id) }
        let remappedRules = document.timeRules.map { rule -> TimeRule in
            guard let replacement = idMap[rule.termId], replacement != rule.termId else { return rule }
            var updated = rule
            updated.termId = replacement
            return updated
        }
        var ruleIDMap: [String: String] = [:]
        var canonicalRules: [TimeRule] = []
        for group in Dictionary(grouping: remappedRules, by: \.termId).values {
            guard let selected = group.max(by: { LocalValue.instant($0.updatedAt) < LocalValue.instant($1.updatedAt) }) else { continue }
            canonicalRules.append(selected)
            for rule in group { ruleIDMap[rule.id] = selected.id }
        }
        canonicalRules.sort { $0.termId < $1.termId }

        let events = document.events.map { event -> ScheduleEvent in
            var updated = event
            if let termID = event.termId { updated.termId = idMap[termID] ?? termID }
            if let ruleID = event.timeRuleId { updated.timeRuleId = ruleIDMap[ruleID] ?? ruleID }
            return updated
        }
        var settings = document.settings
        if let active = settings.activeTermId { settings.activeTermId = idMap[active] ?? active }
        let liveIDs: [EntityType: Set<String>] = [
            .TERM: Set(canonicalTerms.map(\.id)),
            .TIME_RULE: Set(canonicalRules.map(\.id)),
            .EVENT: Set(events.map(\.id)),
        ]
        let remappedTombstones = document.tombstones.compactMap { tombstone -> Tombstone? in
            var updated = tombstone
            switch tombstone.entityType {
            case .TERM: updated.id = idMap[tombstone.id] ?? tombstone.id
            case .TIME_RULE: updated.id = ruleIDMap[tombstone.id] ?? tombstone.id
            case .EVENT: break
            }
            return liveIDs[updated.entityType]?.contains(updated.id) == true ? nil : updated
        }
        let tombstones = Dictionary(
            grouping: remappedTombstones,
            by: { "\($0.entityType.rawValue)|\($0.id)" }
        ).values.compactMap { group in
            group.max(by: { LocalValue.instant($0.deletedAt) < LocalValue.instant($1.deletedAt) })
        }.sorted { "\($0.entityType.rawValue)|\($0.id)" < "\($1.entityType.rawValue)|\($1.id)" }

        var next = document
        next.terms = canonicalTerms
        next.timeRules = canonicalRules
        next.events = events
        next.tombstones = tombstones
        next.settings = settings
        let changed = next != document
        return DocumentMigrationResult(
            document: next,
            changed: changed,
            mergedTermCount: max(0, document.terms.count - next.terms.count)
        )
    }

    static func naturalKey(_ term: Term) -> String {
        guard !term.academicYear.isEmpty, !term.semesterCode.isEmpty else {
            return "legacy-id\u{001F}\(term.id)"
        }
        return ProviderTermIdentity.naturalKey(
            providerId: ProviderTermIdentity.inferredProviderID(for: term),
            school: term.school,
            campus: term.campus,
            academicYear: term.academicYear,
            semesterCode: term.semesterCode
        )
    }

    static func canonicalID(for term: Term) -> String {
        guard !term.academicYear.isEmpty, !term.semesterCode.isEmpty else { return term.id }
        return ProviderTermIdentity.canonicalID(
            providerId: ProviderTermIdentity.inferredProviderID(for: term),
            school: term.school,
            campus: term.campus,
            academicYear: term.academicYear,
            semesterCode: term.semesterCode
        )
    }
}
