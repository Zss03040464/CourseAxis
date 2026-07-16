package dev.shusenzheng64.courseaxis.core

object WeekPagerPolicy {
    fun pageForWeek(week: Int, totalWeeks: Int): Int =
        week.coerceIn(1, totalWeeks.coerceAtLeast(1)) - 1

    fun weekForPage(page: Int, totalWeeks: Int): Int =
        page.coerceIn(0, totalWeeks.coerceAtLeast(1) - 1) + 1
}
