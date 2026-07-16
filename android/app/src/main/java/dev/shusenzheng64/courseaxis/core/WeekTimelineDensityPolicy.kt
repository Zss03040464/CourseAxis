package dev.shusenzheng64.courseaxis.core

data class WeekCourseCardDensity(
    val titleMaxLines: Int,
    val showTime: Boolean,
    val showLocation: Boolean,
    val showTeacher: Boolean,
    val showWeeks: Boolean,
)

object WeekTimelineDensityPolicy {
    fun showEndTime(slotHeightDp: Float): Boolean = slotHeightDp >= 34f

    fun courseCard(heightDp: Float, isExam: Boolean): WeekCourseCardDensity {
        val contentHeight = heightDp - if (isExam) 8f else 0f
        return WeekCourseCardDensity(
            titleMaxLines = when {
                contentHeight >= 72f -> 3
                contentHeight >= 34f -> 2
                else -> 1
            },
            showTime = contentHeight >= 28f,
            showLocation = contentHeight >= 38f,
            showTeacher = contentHeight >= 46f,
            showWeeks = contentHeight >= 58f,
        )
    }
}
