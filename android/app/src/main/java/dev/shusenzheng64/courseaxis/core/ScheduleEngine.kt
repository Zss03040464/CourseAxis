package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.EventOccurrence
import dev.shusenzheng64.courseaxis.model.LanePlacement
import dev.shusenzheng64.courseaxis.model.Recurrence
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.Term
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

object ScheduleEngine {
    fun dateFor(term: Term, week: Int, weekday: Int): LocalDate? =
        if (week in 1..term.totalWeeks && weekday in 1..7) {
            term.firstWeekMondayDate().plusDays((week - 1) * 7L + (weekday - 1))
        } else {
            null
        }

    fun weekFor(term: Term, date: LocalDate): Int? {
        val days = date.toEpochDay() - term.firstWeekMondayDate().toEpochDay()
        if (days < 0) return null
        return ((days / 7).toInt() + 1).takeIf { it in 1..term.totalWeeks }
    }

    fun occurrenceOn(
        event: ScheduleEvent,
        document: ScheduleDocument,
        date: LocalDate,
    ): EventOccurrence? {
        val start = event.startLocalTime() ?: return null
        val end = event.endLocalTime() ?: return null
        if (end <= start) return null
        val occurs = when (event.recurrence) {
            Recurrence.SINGLE -> event.localDate() == date
            Recurrence.WEEKLY -> {
                val term = event.termId?.let { id -> document.terms.firstOrNull { it.id == id } }
                    ?: return null
                val week = weekFor(term, date) ?: return null
                event.weekday == date.dayOfWeek.value && week in event.weeks
            }
        }
        return if (occurs) EventOccurrence(event, date, start, end) else null
    }

    fun occurrencesOn(document: ScheduleDocument, date: LocalDate): List<EventOccurrence> =
        document.events
            .mapNotNull { occurrenceOn(it, document, date) }
            .sortedWith(compareBy<EventOccurrence> { it.start }.thenBy { it.end }.thenBy { it.event.title })

    fun occurrencesForWeek(
        document: ScheduleDocument,
        term: Term,
        week: Int,
    ): Map<LocalDate, List<EventOccurrence>> =
        (1..7).mapNotNull { day ->
            dateFor(term, week, day)?.let { it to occurrencesOn(document, it) }
        }.toMap()

    fun overlaps(a: EventOccurrence, b: EventOccurrence): Boolean =
        a.date == b.date && a.start < b.end && b.start < a.end

    fun overlapMinutes(a: EventOccurrence, b: EventOccurrence): Int =
        if (!overlaps(a, b)) 0
        else Duration.between(maxOf(a.start, b.start), minOf(a.end, b.end)).toMinutes().toInt()

    fun conflicts(
        candidate: ScheduleEvent,
        document: ScheduleDocument,
    ): List<Pair<EventOccurrence, EventOccurrence>> {
        val dates = when (candidate.recurrence) {
            Recurrence.SINGLE -> listOfNotNull(candidate.localDate())
            Recurrence.WEEKLY -> {
                val term = candidate.termId?.let { id -> document.terms.firstOrNull { it.id == id } }
                    ?: return emptyList()
                candidate.weeks.mapNotNull { week ->
                    candidate.weekday?.let { dateFor(term, week, it) }
                }
            }
        }
        return dates.flatMap { date ->
            val candidateOccurrence = occurrenceOn(candidate, document, date)
                ?: return@flatMap emptyList()
            occurrencesOn(document, date)
                .filter { it.event.id != candidate.id && overlaps(candidateOccurrence, it) }
                .map { candidateOccurrence to it }
        }
    }

    fun assignLanes(occurrences: List<EventOccurrence>): List<LanePlacement> {
        if (occurrences.isEmpty()) return emptyList()
        val sorted = occurrences.sortedWith(compareBy<EventOccurrence> { it.start }.thenBy { it.end })
        val groups = mutableListOf<MutableList<EventOccurrence>>()
        var current = mutableListOf<EventOccurrence>()
        var end = sorted.first().end
        for (occurrence in sorted) {
            if (current.isNotEmpty() && occurrence.start >= end) {
                groups += current
                current = mutableListOf()
                end = occurrence.end
            }
            current += occurrence
            if (occurrence.end > end) end = occurrence.end
        }
        if (current.isNotEmpty()) groups += current
        return groups.flatMap { group ->
            val laneEnds = mutableListOf<LocalTime>()
            val indexed = group.map { occurrence ->
                val free = laneEnds.indexOfFirst { it <= occurrence.start }
                val lane = if (free >= 0) free else laneEnds.size
                if (free >= 0) laneEnds[free] = occurrence.end else laneEnds += occurrence.end
                occurrence to lane
            }
            val count = laneEnds.size.coerceAtLeast(1)
            indexed.map { LanePlacement(it.first, it.second, count) }
        }
    }

    fun nextOccurrences(
        document: ScheduleDocument,
        nowDate: LocalDate,
        nowTime: LocalTime,
        limit: Int,
    ): List<EventOccurrence> {
        if (limit <= 0) return emptyList()
        return document.events
            .asSequence()
            .flatMap { event ->
                when (event.recurrence) {
                    Recurrence.SINGLE -> listOfNotNull(
                        event.localDate()?.let { date -> occurrenceOn(event, document, date) },
                    ).asSequence()
                    Recurrence.WEEKLY -> {
                        val term = event.termId
                            ?.let { termId -> document.terms.firstOrNull { it.id == termId } }
                        if (term == null || event.weekday == null) {
                            emptySequence()
                        } else {
                            event.weeks.asSequence().mapNotNull { week ->
                                dateFor(term, week, event.weekday)
                                    ?.let { date -> occurrenceOn(event, document, date) }
                            }
                        }
                    }
                }
            }
            .filter { it.date > nowDate || (it.date == nowDate && it.start > nowTime) }
            .sortedWith(
                compareBy<EventOccurrence> { it.date }
                    .thenBy { it.start }
                    .thenBy { it.end }
                    .thenBy { it.event.title },
            )
            .take(limit)
            .toList()
    }

    fun nextBoundary(
        document: ScheduleDocument,
        nowDate: LocalDate,
        nowTime: LocalTime,
    ): Pair<LocalDate, LocalTime>? {
        val currentEnd = occurrencesOn(document, nowDate)
            .firstOrNull { it.start <= nowTime && nowTime < it.end }
            ?.let { nowDate to it.end }
        val upcomingStart = nextOccurrences(document, nowDate, nowTime, limit = 1)
            .firstOrNull()
            ?.let { it.date to it.start }
        return listOfNotNull(currentEnd, upcomingStart)
            .minWithOrNull(compareBy<Pair<LocalDate, LocalTime>> { it.first }.thenBy { it.second })
    }
}
