package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.EventKind
import dev.shusenzheng64.courseaxis.model.Recurrence
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import java.time.LocalDate

data class ExamFocus(
    val termId: String,
    val weekNumber: Int,
    val examCount: Int,
)

object ExamFocusPolicy {
    private data class Group(
        val focus: ExamFocus,
        val lastDate: LocalDate,
    )

    fun resolve(document: ScheduleDocument, termId: String): ExamFocus? {
        val terms = document.terms.associateBy { it.id }
        val groups = document.events
            .asSequence()
            .filter {
                it.termId == termId &&
                    it.kind == EventKind.EXAM &&
                    it.recurrence == Recurrence.SINGLE
            }
            .mapNotNull { event ->
                val eventTermId = event.termId ?: return@mapNotNull null
                val term = terms[eventTermId] ?: return@mapNotNull null
                val date = event.localDate() ?: return@mapNotNull null
                val week = ScheduleEngine.weekFor(term, date) ?: return@mapNotNull null
                Triple(eventTermId, week, date)
            }
            .groupBy { it.first to it.second }
            .map { (key, items) ->
                Group(
                    focus = ExamFocus(key.first, key.second, items.size),
                    lastDate = items.maxOf { it.third },
                )
            }

        return groups.maxWithOrNull(
            compareBy<Group> { it.focus.examCount }
                .thenBy { it.lastDate }
                .thenBy { "${it.focus.termId}:${it.focus.weekNumber}" },
        )?.focus
    }
}
