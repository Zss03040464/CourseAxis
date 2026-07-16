package dev.shusenzheng64.courseaxis.core

import java.time.LocalDate

enum class SemesterOption(
    val code: String,
    val label: String,
) {
    FIRST("3", "第一学期"),
    SECOND("12", "第二学期"),
    THIRD("16", "第三学期"),
}

object TermSetupPolicy {
    val semesterOptions: List<SemesterOption> = SemesterOption.entries

    fun semesterForCode(code: String): SemesterOption? =
        semesterOptions.firstOrNull { it.code == code.trim() }

    fun standardName(academicYear: String, semester: SemesterOption): String {
        val startYear = academicYear.trim().toIntOrNull()
            ?: error("请选择学年")
        return "$startYear-${startYear + 1} ${semester.label}"
    }

    fun academicYearLabel(academicYear: String): String {
        val startYear = academicYear.trim().toIntOrNull()
            ?: error("请选择学年")
        return "$startYear–${startYear + 1} 学年"
    }

    fun academicYearOptions(current: String, today: LocalDate = LocalDate.now()): List<String> {
        val referenceStart = if (today.monthValue >= 8) today.year else today.year - 1
        return ((referenceStart - 2)..(referenceStart + 2))
            .map { it.toString() }
            .plus(current.trim().takeIf { it.toIntOrNull() != null })
            .filterNotNull()
            .distinct()
            .sortedDescending()
    }

    fun totalWeekOptions(current: Int): List<Int> =
        listOf(16, 18, 20, 22, 24)
            .plus(current.takeIf { it in 1..40 })
            .filterNotNull()
            .distinct()
            .sorted()
}
