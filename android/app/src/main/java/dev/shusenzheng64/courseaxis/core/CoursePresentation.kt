package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.EventKind
import dev.shusenzheng64.courseaxis.model.Recurrence
import dev.shusenzheng64.courseaxis.model.ScheduleEvent

object CoursePresentation {
    fun fullDetails(event: ScheduleEvent): List<String> = buildList {
        add("教师：${event.teacher.ifBlank { "待确认" }}")
        add("地点：${event.location.ifBlank { "待确认" }}")
        add("时间：${event.startTime.orEmpty().ifBlank { "待确认" }}–${event.endTime.orEmpty().ifBlank { "待确认" }}")
        add("周次：${fullWeekText(event)}")
        if (event.kind == EventKind.COURSE) {
            add("性质：${event.courseNature.ifBlank { "待确认" }}")
            add("总学时：${event.totalHours.ifBlank { "待确认" }}")
            add("学分：${event.credits.ifBlank { "待确认" }}")
        }
    }

    fun fullWeekText(event: ScheduleEvent): String {
        if (event.recurrence == Recurrence.SINGLE) return "单次"
        val values = event.weeks.sorted()
        if (values.size > 1 && values.zipWithNext().all { (a, b) -> b - a == 2 }) {
            return "${values.first()}-${values.last()}周（${if (values.first() % 2 == 0) "双" else "单"}）"
        }
        return WeekParser.format(event.weeks).ifBlank { "待确认" }
    }

    fun compactWeekRange(event: ScheduleEvent): String {
        if (event.recurrence != Recurrence.WEEKLY) return ""
        val first = event.weeks.minOrNull() ?: return ""
        val last = event.weeks.maxOrNull() ?: return ""
        return "$first-$last"
    }

    fun timelineDetails(event: ScheduleEvent): List<String> = listOf(
        listOf(
            event.teacher.ifBlank { "教师待确认" },
            event.location.ifBlank { "地点待确认" },
        ).joinToString(" · "),
        fullWeekText(event),
    )
}
