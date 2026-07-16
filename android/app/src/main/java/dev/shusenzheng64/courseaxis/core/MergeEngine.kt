package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.data.DocumentMigration
import dev.shusenzheng64.courseaxis.model.EntityType
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.Tombstone
import java.time.Instant

object MergeEngine {
    fun merge(local: ScheduleDocument, remote: ScheduleDocument): ScheduleDocument {
        val migratedLocal = DocumentMigration.migrate(local).document
        val migratedRemote = DocumentMigration.migrate(remote).document
        require(migratedLocal.schemaVersion == migratedRemote.schemaVersion) { "Schema version mismatch" }

        fun instant(value: String): Instant =
            runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)

        val tombstones = (migratedLocal.tombstones + migratedRemote.tombstones)
            .groupBy { it.entityType to it.id }
            .values
            .map { group -> group.maxBy { instant(it.deletedAt) } }
        val tombstoneMap = tombstones.associateBy { it.entityType to it.id }

        fun <T> records(
            localRecords: List<T>,
            remoteRecords: List<T>,
            id: (T) -> String,
            updatedAt: (T) -> String,
            type: EntityType,
        ): List<T> = (localRecords + remoteRecords)
            .groupBy(id)
            .values
            .map { group -> group.maxBy { instant(updatedAt(it)) } }
            .filter { value ->
                tombstoneMap[type to id(value)]
                    ?.let { instant(it.deletedAt) >= instant(updatedAt(value)) } != true
            }

        val localUpdatedAt = instant(migratedLocal.updatedAt)
        val remoteUpdatedAt = instant(migratedRemote.updatedAt)
        return ScheduleDocument(
            schemaVersion = migratedLocal.schemaVersion,
            documentId = migratedLocal.documentId,
            updatedAt = maxOf(localUpdatedAt, remoteUpdatedAt).toString(),
            terms = records(
                migratedLocal.terms,
                migratedRemote.terms,
                { it.id },
                { it.updatedAt },
                EntityType.TERM,
            ),
            timeRules = records(
                migratedLocal.timeRules,
                migratedRemote.timeRules,
                { it.id },
                { it.updatedAt },
                EntityType.TIME_RULE,
            ),
            events = records(
                migratedLocal.events,
                migratedRemote.events,
                { it.id },
                { it.updatedAt },
                EntityType.EVENT,
            ),
            tombstones = tombstones.sortedWith(
                compareBy<Tombstone> { it.entityType.name }.thenBy { it.id },
            ),
            settings = if (localUpdatedAt >= remoteUpdatedAt) migratedLocal.settings else migratedRemote.settings,
        )
    }
}
