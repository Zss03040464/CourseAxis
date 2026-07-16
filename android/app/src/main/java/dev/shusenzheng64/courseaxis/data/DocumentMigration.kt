package dev.shusenzheng64.courseaxis.data

import dev.shusenzheng64.courseaxis.core.StableId
import dev.shusenzheng64.courseaxis.model.EntityType
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.Term
import dev.shusenzheng64.courseaxis.model.TimeRule
import dev.shusenzheng64.courseaxis.model.Tombstone
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

object ProviderTermIdentity {
    const val DEFAULT_PROVIDER_ID = "nfu-zhengfang"
    const val DEFAULT_PROVIDER_SCHOOL = "广州南方学院"

    fun inferredProviderId(term: Term): String? = when {
        !term.providerId.isNullOrBlank() -> normalize(term.providerId)
        term.school.trim() == DEFAULT_PROVIDER_SCHOOL -> DEFAULT_PROVIDER_ID
        else -> null
    }

    fun naturalKey(
        providerId: String?,
        school: String,
        campus: String,
        academicYear: String,
        semesterCode: String,
    ): String = listOf(
        identityScope(providerId, school),
        normalize(campus),
        normalize(academicYear),
        normalize(semesterCode),
    ).joinToString("\u001F")

    fun canonicalID(
        providerId: String?,
        school: String,
        campus: String,
        academicYear: String,
        semesterCode: String,
    ): String = StableId.of(
        "term",
        identityScope(providerId, school),
        normalize(campus),
        normalize(academicYear),
        normalize(semesterCode),
    )

    private fun identityScope(providerId: String?, school: String): String =
        normalize(providerId?.takeIf { it.isNotBlank() } ?: school)

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
}

data class DocumentMigrationResult(
    val document: ScheduleDocument,
    val changed: Boolean,
    val mergedTermCount: Int,
)

object DocumentMigration {
    fun migrate(document: ScheduleDocument): DocumentMigrationResult {
        val normalizedTerms = document.terms.map { term ->
            term.copy(providerId = ProviderTermIdentity.inferredProviderId(term))
        }
        val idMap = mutableMapOf<String, String>()
        val canonicalTerms = normalizedTerms
            .groupBy(::naturalKey)
            .values
            .mapNotNull { group ->
                val selected = group.maxByOrNull { instant(it.updatedAt) } ?: return@mapNotNull null
                val canonicalID = canonicalID(selected)
                group.forEach { term -> idMap[term.id] = canonicalID }
                selected.copy(id = canonicalID)
            }
            .sortedWith(compareBy<Term> { it.academicYear }.thenBy { it.semesterCode }.thenBy { it.id })

        val remappedRules = document.timeRules.map { rule ->
            rule.copy(termId = idMap[rule.termId] ?: rule.termId)
        }
        val ruleIdMap = mutableMapOf<String, String>()
        val canonicalRules = remappedRules
            .groupBy(TimeRule::termId)
            .values
            .mapNotNull { group ->
                val selected = group.maxByOrNull { instant(it.updatedAt) } ?: return@mapNotNull null
                group.forEach { rule -> ruleIdMap[rule.id] = selected.id }
                selected
            }
            .sortedBy(TimeRule::termId)

        val events = document.events.map { event ->
            event.copy(
                termId = event.termId?.let { idMap[it] ?: it },
                timeRuleId = event.timeRuleId?.let { ruleIdMap[it] ?: it },
            )
        }
        val settings = document.settings.copy(
            activeTermId = document.settings.activeTermId?.let { idMap[it] ?: it },
        )
        val liveIDs = mapOf(
            EntityType.TERM to canonicalTerms.mapTo(mutableSetOf(), Term::id),
            EntityType.TIME_RULE to canonicalRules.mapTo(mutableSetOf(), TimeRule::id),
            EntityType.EVENT to events.mapTo(mutableSetOf()) { it.id },
        )
        val tombstones = document.tombstones
            .map { tombstone ->
                when (tombstone.entityType) {
                    EntityType.TERM -> tombstone.copy(id = idMap[tombstone.id] ?: tombstone.id)
                    EntityType.TIME_RULE -> tombstone.copy(id = ruleIdMap[tombstone.id] ?: tombstone.id)
                    EntityType.EVENT -> tombstone
                }
            }
            .filterNot { tombstone -> liveIDs[tombstone.entityType]?.contains(tombstone.id) == true }
            .groupBy { it.entityType to it.id }
            .values
            .map { group -> group.maxBy { instant(it.deletedAt) } }
            .sortedWith(compareBy<Tombstone> { it.entityType.name }.thenBy { it.id })

        val migrated = document.copy(
            terms = canonicalTerms,
            timeRules = canonicalRules,
            events = events,
            tombstones = tombstones,
            settings = settings,
        )
        return DocumentMigrationResult(
            document = migrated,
            changed = migrated != document,
            mergedTermCount = (document.terms.size - canonicalTerms.size).coerceAtLeast(0),
        )
    }

    fun naturalKey(term: Term): String {
        if (term.academicYear.isBlank() || term.semesterCode.isBlank()) return "legacy-id\u001F${term.id}"
        return ProviderTermIdentity.naturalKey(
            providerId = ProviderTermIdentity.inferredProviderId(term),
            school = term.school,
            campus = term.campus,
            academicYear = term.academicYear,
            semesterCode = term.semesterCode,
        )
    }

    fun canonicalID(term: Term): String {
        if (term.academicYear.isBlank() || term.semesterCode.isBlank()) return term.id
        return ProviderTermIdentity.canonicalID(
            providerId = ProviderTermIdentity.inferredProviderId(term),
            school = term.school,
            campus = term.campus,
            academicYear = term.academicYear,
            semesterCode = term.semesterCode,
        )
    }

    private fun instant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
}
