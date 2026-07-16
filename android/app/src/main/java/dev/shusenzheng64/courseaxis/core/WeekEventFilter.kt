package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.EventKind

enum class WeekEventMode { ALL, COURSE, EXAM }

object WeekEventFilter {
    fun apply(items: List<ProjectedOccurrence>, mode: WeekEventMode): List<ProjectedOccurrence> =
        when (mode) {
            WeekEventMode.ALL -> items
            WeekEventMode.COURSE -> items.filter { it.event.kind == EventKind.COURSE }
            WeekEventMode.EXAM -> items.filter { it.event.kind == EventKind.EXAM }
        }
}
