package dev.shusenzheng64.courseaxis.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class EventKind { COURSE, EXAM, CUSTOM, ADJUSTMENT }
enum class Recurrence { SINGLE, WEEKLY }
enum class TimeSource { EXPLICIT, SECTION_RULE, USER, DATE_OVERRIDE, UNRESOLVED }
enum class ResolutionStatus { RESOLVED, UNRESOLVED, NEEDS_REVIEW }
enum class TimeRuleSource { JIAOWU_DOM, JIAOWU_EXPLICIT, USER, MIGRATED }
enum class EntityType { EVENT, TERM, TIME_RULE }

data class SectionTime(val section: Int, val start: String, val end: String) {
    fun startLocalTime(): LocalTime = LocalTime.parse(start)
    fun endLocalTime(): LocalTime = LocalTime.parse(end)
}

data class Term(
    val id: String,
    val name: String,
    val school: String = "",
    val campus: String = "",
    val academicYear: String = "",
    val semesterCode: String = "",
    val firstWeekMonday: String,
    val totalWeeks: Int = 20,
    val timeZone: String = "Asia/Shanghai",
    val updatedAt: String = Instant.now().toString(),
    val providerId: String? = null,
) { fun firstWeekMondayDate(): LocalDate = LocalDate.parse(firstWeekMonday) }

data class TimeRule(
    val id: String,
    val termId: String,
    val school: String = "",
    val campus: String = "",
    val source: TimeRuleSource = TimeRuleSource.USER,
    val sections: List<SectionTime> = emptyList(),
    val updatedAt: String = Instant.now().toString(),
)

data class ScheduleEvent(
    val id: String,
    val externalId: String? = null,
    val kind: EventKind,
    val recurrence: Recurrence,
    val title: String,
    val teacher: String = "",
    val location: String = "",
    val campus: String = "",
    val notes: String = "",
    val termId: String? = null,
    val date: String? = null,
    val weekday: Int? = null,
    val weeks: Set<Int> = emptySet(),
    val startTime: String? = null,
    val endTime: String? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val timeRuleId: String? = null,
    val timeSource: TimeSource = TimeSource.UNRESOLVED,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.UNRESOLVED,
    val colorKey: Int = 0,
    val updatedAt: String = Instant.now().toString(),
    val courseNature: String = "",
    val totalHours: String = "",
    val credits: String = "",
) {
    fun startLocalTime(): LocalTime? = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    fun endLocalTime(): LocalTime? = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    fun localDate(): LocalDate? = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun isResolved(): Boolean = resolutionStatus == ResolutionStatus.RESOLVED && startLocalTime() != null && endLocalTime() != null
}

data class Tombstone(val id: String, val entityType: EntityType, val deletedAt: String = Instant.now().toString())
data class AppSettings(
    val activeTermId: String? = null,
    val dayStart: String = "07:30",
    val dayEnd: String = "22:30",
    val showWeekends: Boolean = true,
    val widgetUpcomingCount: Int = 5,
    val selectedAcademicProviderId: String = "nfu-zhengfang",
)

data class ScheduleDocument(
    val schemaVersion: Int = 1,
    val documentId: String = UUID.randomUUID().toString(),
    val updatedAt: String = Instant.now().toString(),
    val terms: List<Term> = emptyList(),
    val timeRules: List<TimeRule> = emptyList(),
    val events: List<ScheduleEvent> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    fun activeTerm(): Term? = settings.activeTermId?.let { id -> terms.firstOrNull { it.id == id } } ?: terms.maxByOrNull { it.updatedAt }
    fun timeRuleFor(termId: String?): TimeRule? = termId?.let { id -> timeRules.filter { it.termId == id }.maxByOrNull { it.updatedAt } }
    companion object { fun empty() = ScheduleDocument() }
}

data class EventOccurrence(val event: ScheduleEvent, val date: LocalDate, val start: LocalTime, val end: LocalTime)
data class SectionMatch(val startSection: Int, val endSection: Int, val exact: Boolean, val coverageMinutes: Int)
data class LanePlacement(val occurrence: EventOccurrence, val laneIndex: Int, val laneCount: Int)
