package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.Recurrence
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.Term
import java.time.LocalDate
import java.time.LocalTime

data class ProjectedOccurrence(
    val event: ScheduleEvent,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val occursThisWeek: Boolean,
)

object WeekProjectionEngine {
    fun itemsInWeek(week: Int, term: Term, document: ScheduleDocument): List<ProjectedOccurrence> =
        (1..7).flatMap { weekday ->
            ScheduleEngine.dateFor(term, week, weekday)
                ?.let { itemsOn(it, week, term, document) }
                .orEmpty()
        }

    fun itemsOn(
        date: LocalDate,
        week: Int,
        term: Term,
        document: ScheduleDocument,
    ): List<ProjectedOccurrence> {
        val raw = document.events.mapNotNull { event ->
            if (event.termId != term.id || !event.isResolved()) return@mapNotNull null
            val start = event.startLocalTime() ?: return@mapNotNull null
            val end = event.endLocalTime() ?: return@mapNotNull null
            when (event.recurrence) {
                Recurrence.SINGLE -> {
                    if (event.localDate() != date) null
                    else ProjectedOccurrence(event, date, start, end, true)
                }
                Recurrence.WEEKLY -> {
                    val lastWeek = event.weeks.maxOrNull() ?: return@mapNotNull null
                    if (event.weekday != date.dayOfWeek.value || week > lastWeek) null
                    else ProjectedOccurrence(event, date, start, end, week in event.weeks)
                }
            }
        }
        val active = raw.filter { it.occursThisWeek }
        return raw.filter { candidate ->
            candidate.occursThisWeek || active.none { current ->
                current.event.title == candidate.event.title &&
                    current.event.teacher == candidate.event.teacher &&
                    current.start < candidate.end && candidate.start < current.end
            }
        }.sortedWith(
            compareBy<ProjectedOccurrence> { it.start }
                .thenByDescending { it.occursThisWeek }
                .thenBy { it.end }
                .thenBy { it.event.title }
                .thenBy { it.event.id },
        )
    }
}
