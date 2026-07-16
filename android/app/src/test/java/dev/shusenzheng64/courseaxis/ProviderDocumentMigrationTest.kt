package dev.shusenzheng64.courseaxis

import dev.shusenzheng64.courseaxis.data.DocumentMigration
import dev.shusenzheng64.courseaxis.data.ProviderTermIdentity
import dev.shusenzheng64.courseaxis.data.ScheduleJsonCodec
import dev.shusenzheng64.courseaxis.model.AppSettings
import dev.shusenzheng64.courseaxis.model.EntityType
import dev.shusenzheng64.courseaxis.model.EventKind
import dev.shusenzheng64.courseaxis.model.Recurrence
import dev.shusenzheng64.courseaxis.model.ResolutionStatus
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.SectionTime
import dev.shusenzheng64.courseaxis.model.Term
import dev.shusenzheng64.courseaxis.model.TimeRule
import dev.shusenzheng64.courseaxis.model.TimeSource
import dev.shusenzheng64.courseaxis.model.Tombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDocumentMigrationTest {
    @Test
    fun legacyJsonInfersNfuProviderAndDefaultsProviderSelection() {
        val legacy = """{
            "schemaVersion":1,
            "documentId":"legacy-provider",
            "updatedAt":"2026-07-15T00:00:00Z",
            "terms":[{
                "id":"legacy-term","name":"2026-2027 第一学期",
                "school":"广州南方学院","campus":"温泉校区",
                "academicYear":"2026","semesterCode":"3",
                "firstWeekMonday":"2026-09-07","totalWeeks":20,
                "timeZone":"Asia/Shanghai","updatedAt":"2026-07-15T00:00:00Z"
            }],
            "timeRules":[],"events":[],"tombstones":[],
            "settings":{"activeTermId":"legacy-term","dayStart":"07:30","dayEnd":"22:30","showWeekends":true}
        }""".trimIndent()

        val decoded = ScheduleJsonCodec.decode(legacy)

        assertEquals("nfu-zhengfang", decoded.terms.single().providerId)
        assertEquals("term-376b9897fd1a5c7a", decoded.terms.single().id)
        assertEquals(decoded.terms.single().id, decoded.settings.activeTermId)
        assertEquals("nfu-zhengfang", decoded.settings.selectedAcademicProviderId)
    }

    @Test
    fun providerAwareMigrationScopesTermsAndRewritesReferences() {
        val older = term("legacy-nfu-old", "2026-09-01", "2026-07-01T00:00:00Z")
        val newer = term("legacy-nfu-new", "2026-09-07", "2026-07-02T00:00:00Z")
        val otherProvider = term(
            id = "other-provider",
            firstMonday = "2026-09-07",
            updatedAt = "2026-07-03T00:00:00Z",
            providerId = "other-zhengfang",
        )
        val manual = term(
            id = "manual-term",
            firstMonday = "2026-09-07",
            updatedAt = "2026-07-04T00:00:00Z",
            providerId = null,
            school = "示例手工学校",
        )
        val oldRule = TimeRule(
            id = "rule-old",
            termId = older.id,
            sections = listOf(SectionTime(1, "08:00", "08:40")),
            updatedAt = "2026-07-01T00:00:00Z",
        )
        val newRule = TimeRule(
            id = "rule-new",
            termId = newer.id,
            sections = listOf(SectionTime(1, "08:10", "08:50")),
            updatedAt = "2026-07-02T00:00:00Z",
        )
        val legacyCourse = ScheduleEvent(
            id = "legacy-course",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "示例课程",
            termId = older.id,
            weekday = 1,
            weeks = setOf(1),
            startTime = "08:00",
            endTime = "08:40",
            timeRuleId = oldRule.id,
            timeSource = TimeSource.SECTION_RULE,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(
            terms = listOf(older, newer, otherProvider, manual),
            timeRules = listOf(oldRule, newRule),
            events = listOf(legacyCourse),
            tombstones = listOf(
                Tombstone(older.id, EntityType.TERM),
                Tombstone(oldRule.id, EntityType.TIME_RULE),
                Tombstone("deleted-event", EntityType.EVENT),
            ),
            settings = AppSettings(
                activeTermId = older.id,
                selectedAcademicProviderId = "nfu-zhengfang",
            ),
        )

        val result = DocumentMigration.migrate(document)
        val canonical = "term-376b9897fd1a5c7a"

        assertTrue(result.changed)
        assertEquals(3, result.document.terms.size)
        assertEquals("nfu-zhengfang", result.document.terms.single { it.id == canonical }.providerId)
        assertEquals("other-zhengfang", result.document.terms.single { it.providerId == "other-zhengfang" }.providerId)
        assertNull(result.document.terms.single { it.school == "示例手工学校" }.providerId)
        assertEquals(canonical, result.document.events.single().termId)
        assertEquals(newRule.id, result.document.events.single().timeRuleId)
        assertEquals(canonical, result.document.timeRules.single().termId)
        assertEquals(canonical, result.document.settings.activeTermId)
        assertEquals("nfu-zhengfang", result.document.settings.selectedAcademicProviderId)
        assertFalse(result.document.tombstones.any { it.id in setOf(older.id, oldRule.id) })
        assertTrue(result.document.tombstones.any { it.id == "deleted-event" })
    }

    @Test
    fun providerTermIdentityIsCrossPlatformAndIgnoresEditableDates() {
        val first = ProviderTermIdentity.canonicalID(
            providerId = "nfu-zhengfang",
            school = "广州南方学院",
            campus = "温泉校区",
            academicYear = "2026",
            semesterCode = "3",
        )
        val second = ProviderTermIdentity.canonicalID(
            providerId = "nfu-zhengfang",
            school = "不同展示名称",
            campus = "温泉校区",
            academicYear = "2026",
            semesterCode = "3",
        )

        assertEquals("term-376b9897fd1a5c7a", first)
        assertEquals(first, second)
    }

    @Test
    fun incompleteManualTermsNeverMerge() {
        val first = Term("manual-a", "A", firstWeekMonday = "2026-09-07")
        val second = Term("manual-b", "B", firstWeekMonday = "2026-09-07")

        val migrated = DocumentMigration.migrate(ScheduleDocument(terms = listOf(first, second))).document

        assertEquals(setOf("manual-a", "manual-b"), migrated.terms.mapTo(mutableSetOf()) { it.id })
    }

    private fun term(
        id: String,
        firstMonday: String,
        updatedAt: String,
        providerId: String? = null,
        school: String = "广州南方学院",
    ) = Term(
        id = id,
        name = "2026-2027 第一学期",
        providerId = providerId,
        school = school,
        campus = "温泉校区",
        academicYear = "2026",
        semesterCode = "3",
        firstWeekMonday = firstMonday,
        updatedAt = updatedAt,
    )
}
