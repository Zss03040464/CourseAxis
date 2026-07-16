import Foundation

enum MergeEngine {
    static func merge(_ local:ScheduleDocument,_ remote:ScheduleDocument)->ScheduleDocument{
        let local=DocumentMigration.migrate(local).document,remote=DocumentMigration.migrate(remote).document
        precondition(local.schemaVersion==remote.schemaVersion)
        let tombstones=Dictionary(grouping:local.tombstones+remote.tombstones,by:{"\($0.entityType.rawValue)|\($0.id)"}).values.compactMap{$0.max{LocalValue.instant($0.deletedAt)<LocalValue.instant($1.deletedAt)}}
        let tomb=Dictionary(uniqueKeysWithValues:tombstones.map{("\($0.entityType.rawValue)|\($0.id)",$0)})
        func deleted(_ type:EntityType,_ id:String,_ updated:String)->Bool{guard let t=tomb["\(type.rawValue)|\(id)"]else{return false};return LocalValue.instant(t.deletedAt)>=LocalValue.instant(updated)}
        let terms=Dictionary(grouping:local.terms+remote.terms,by:\.id).values.compactMap{$0.max{LocalValue.instant($0.updatedAt)<LocalValue.instant($1.updatedAt)}}.filter{!deleted(.TERM,$0.id,$0.updatedAt)}
        let rules=Dictionary(grouping:local.timeRules+remote.timeRules,by:\.id).values.compactMap{$0.max{LocalValue.instant($0.updatedAt)<LocalValue.instant($1.updatedAt)}}.filter{!deleted(.TIME_RULE,$0.id,$0.updatedAt)}
        let events=Dictionary(grouping:local.events+remote.events,by:\.id).values.compactMap{$0.max{LocalValue.instant($0.updatedAt)<LocalValue.instant($1.updatedAt)}}.filter{!deleted(.EVENT,$0.id,$0.updatedAt)}
        let localWins=LocalValue.instant(local.updatedAt)>=LocalValue.instant(remote.updatedAt)
        return ScheduleDocument(schemaVersion:local.schemaVersion,documentId:local.documentId,updatedAt:max(LocalValue.instant(local.updatedAt),LocalValue.instant(remote.updatedAt)).iso8601,terms:terms.sorted{$0.id<$1.id},timeRules:rules.sorted{$0.id<$1.id},events:events.sorted{$0.id<$1.id},tombstones:tombstones.sorted{"\($0.entityType.rawValue)|\($0.id)"<"\($1.entityType.rawValue)|\($1.id)"},settings:localWins ? local.settings:remote.settings)
    }
}
private extension Date { var iso8601:String{ISO8601DateFormatter().string(from:self)} }
