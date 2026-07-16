package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.SectionTime
import dev.shusenzheng64.courseaxis.model.TimeRule
import dev.shusenzheng64.courseaxis.model.TimeRuleSource
import java.time.LocalTime

data class ScheduleTemplateParameters(
    val firstStart: LocalTime,
    val lessonMinutes: Int,
    val smallBreakMinutes: Int,
    val largeBreakMinutes: Int,
    val sectionCount: Int,
)

object ScheduleTemplatePolicy {
    val lessonMinuteOptions = listOf(35, 40, 45, 50)
    val smallBreakMinuteOptions = listOf(5, 10, 15)
    val largeBreakMinuteOptions = listOf(30, 40, 50, 60, 70, 90)
    val sectionCountOptions = (10..16).toList()
    private val largeBreakAfterSections = setOf(5, 11)

    fun generate(
        ruleId: String,
        termId: String,
        school: String,
        campus: String,
        parameters: ScheduleTemplateParameters,
    ): TimeRule {
        require(ruleId.isNotBlank() && termId.isNotBlank()) { "学期信息不完整" }
        require(parameters.lessonMinutes in 1..180) { "每节课时长无效" }
        require(parameters.smallBreakMinutes in 0..60) { "小课间时长无效" }
        require(parameters.largeBreakMinutes in 0..240) { "大课间时长无效" }
        require(parameters.sectionCount in 1..24) { "每日节数无效" }

        var startMinute = parameters.firstStart.hour * 60 + parameters.firstStart.minute
        val sections = (1..parameters.sectionCount).map { section ->
            val endMinute = startMinute + parameters.lessonMinutes
            require(endMinute < 24 * 60) { "作息不能跨越午夜" }
            val value = SectionTime(
                section = section,
                start = formatMinute(startMinute),
                end = formatMinute(endMinute),
            )
            if (section < parameters.sectionCount) {
                val breakMinutes = if (section in largeBreakAfterSections) {
                    parameters.largeBreakMinutes
                } else {
                    parameters.smallBreakMinutes
                }
                startMinute = endMinute + breakMinutes
                require(startMinute < 24 * 60) { "作息不能跨越午夜" }
            }
            value
        }

        return TimeRule(
            id = ruleId,
            termId = termId,
            school = school.trim(),
            campus = campus.trim(),
            source = TimeRuleSource.USER,
            sections = sections,
        )
    }

    private fun formatMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
}
