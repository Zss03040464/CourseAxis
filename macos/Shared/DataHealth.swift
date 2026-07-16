import Foundation

struct DataHealthReport: Equatable {
    var semanticDuplicateTermCount: Int
    var orphanEventIDs: [String]
    var orphanRuleIDs: [String]
    var unresolvedEventCount: Int
    var isHealthy: Bool { semanticDuplicateTermCount == 0 && orphanEventIDs.isEmpty && orphanRuleIDs.isEmpty }
}

enum DataHealth {
    static func inspect(_ document: ScheduleDocument) -> DataHealthReport {
        let duplicates = Dictionary(grouping:document.terms,by:DocumentMigration.naturalKey).values.reduce(0) { $0 + max(0,$1.count-1) }
        let termIDs=Set(document.terms.map(\.id))
        let orphanEvents=document.events.filter{event in guard let termID=event.termId else{return false};return !termIDs.contains(termID)}.map(\.id).sorted()
        let orphanRules=document.timeRules.filter{!termIDs.contains($0.termId)}.map(\.id).sorted()
        return DataHealthReport(semanticDuplicateTermCount:duplicates,orphanEventIDs:orphanEvents,orphanRuleIDs:orphanRules,unresolvedEventCount:document.events.filter{!$0.isResolved}.count)
    }
}
