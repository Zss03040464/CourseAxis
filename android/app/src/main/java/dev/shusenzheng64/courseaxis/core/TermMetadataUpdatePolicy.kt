package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.Term

object TermMetadataUpdatePolicy {
    fun apply(document: ScheduleDocument, term: Term): ScheduleDocument = document.copy(
        terms = document.terms.filterNot { it.id == term.id } + term,
        settings = document.settings.copy(activeTermId = term.id),
    )
}
