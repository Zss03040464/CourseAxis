package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.Term
import java.time.LocalDate

object WeekTermSwitchPolicy {
    fun initialWeek(term: Term, today: LocalDate): Int =
        ScheduleEngine.weekFor(term, today)
            ?.coerceIn(1, term.totalWeeks.coerceAtLeast(1))
            ?: 1
}
