package dev.shusenzheng64.courseaxis.core

enum class WeekLayoutMode { AGENDA, TIMELINE }

data class WeekTimelineMetrics(
    val dayWidthDp: Int,
    val dayCount: Int,
    val gutterWidthDp: Int,
    val contentWidthDp: Int,
    val horizontallyScrollable: Boolean,
)

object WeekLayoutPolicy {
    private const val DAY_COUNT = 7
    private const val GUTTER_WIDTH_DP = 48

    fun mode(widthDp: Int): WeekLayoutMode = WeekLayoutMode.TIMELINE

    fun timelineMetrics(widthDp: Int): WeekTimelineMetrics {
        val availableForDays = (widthDp - GUTTER_WIDTH_DP).coerceAtLeast(0)
        val dayWidth = availableForDays / DAY_COUNT
        val contentWidth = GUTTER_WIDTH_DP + dayWidth * DAY_COUNT
        return WeekTimelineMetrics(
            dayWidthDp = dayWidth,
            dayCount = DAY_COUNT,
            gutterWidthDp = GUTTER_WIDTH_DP,
            contentWidthDp = contentWidth,
            horizontallyScrollable = false,
        )
    }

    fun visibleWeekdays(): List<Int> = (1..DAY_COUNT).toList()

    fun sectionSlotHeightDp(availableHeightDp: Int, slotCount: Float): Float {
        if (slotCount <= 0f) return 36f
        return (availableHeightDp / slotCount).coerceIn(24f, 36f)
    }
}
