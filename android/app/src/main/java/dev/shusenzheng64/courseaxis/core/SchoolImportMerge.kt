package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.EventKind
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.TimeSource

object SchoolImportMerge {
    fun mergeEvent(existing: ScheduleEvent?, imported: ScheduleEvent): ScheduleEvent {
        if (existing == null || existing.timeSource != TimeSource.USER) return imported
        return imported.copy(
            startTime = existing.startTime,
            endTime = existing.endTime,
            startSection = existing.startSection,
            endSection = existing.endSection,
            timeRuleId = existing.timeRuleId,
            timeSource = existing.timeSource,
            resolutionStatus = existing.resolutionStatus,
        )
    }

    fun mergeTermEvents(
        existing: List<ScheduleEvent>,
        imported: List<ScheduleEvent>,
        termId: String,
        preserveExistingExams: Boolean = false,
    ): List<ScheduleEvent> {
        val schoolKinds = setOf(EventKind.COURSE, EventKind.EXAM, EventKind.ADJUSTMENT)
        val currentById = existing.associateBy { it.id }
        val importedKinds = imported.mapTo(mutableSetOf()) { it.kind }
        val retained = existing.filter { event ->
            event.termId != termId || event.kind !in schoolKinds ||
                (event.kind == EventKind.EXAM && (preserveExistingExams || EventKind.EXAM !in importedKinds))
        }
        val mergedImports = imported.map { mergeEvent(currentById[it.id], it) }
        val mergedIDs = mergedImports.mapTo(mutableSetOf()) { it.id }
        return retained.filterNot { it.id in mergedIDs } + mergedImports
    }
}
