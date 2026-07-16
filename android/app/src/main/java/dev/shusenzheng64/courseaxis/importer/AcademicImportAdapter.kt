package dev.shusenzheng64.courseaxis.importer

import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.Term
import dev.shusenzheng64.courseaxis.model.TimeRule
import java.time.LocalDate

data class AcademicImportOptions(
    val provider: AcademicProvider,
    val academicYear: String,
    val semesterCode: String,
    val termName: String,
    val firstWeekMonday: LocalDate,
    val totalWeeks: Int,
    val campus: String = "",
)

data class AcademicSourceStatus(
    val ok: Boolean,
    val endpoint: String = "",
    val count: Int = 0,
)

data class AcademicImportBundle(
    val term: Term,
    val timeRule: TimeRule?,
    val events: List<ScheduleEvent>,
    val courseCount: Int,
    val examCount: Int,
    val unresolvedCount: Int,
    val diagnostics: List<String>,
    val courseSource: AcademicSourceStatus = AcademicSourceStatus(ok = true),
    val examSource: AcademicSourceStatus = AcademicSourceStatus(ok = true),
    val sectionSource: AcademicSourceStatus = AcademicSourceStatus(ok = false),
    val courseUnresolvedCount: Int = 0,
    val detectedTerm: Boolean = false,
    val calendarSourceURL: String? = null,
    val canCommit: Boolean = true,
)

interface AcademicImportAdapter {
    val id: String
    val scriptResource: String

    fun makeInvocation(
        provider: AcademicProvider,
        academicYear: String,
        semesterCode: String,
        script: String,
    ): String

    fun parse(raw: String, options: AcademicImportOptions): AcademicImportBundle
}

object AcademicImportAdapterRegistry {
    fun adapter(provider: AcademicProvider): AcademicImportAdapter = when (provider.adapterId) {
        ZhengfangImportAdapter.id -> ZhengfangImportAdapter
        else -> throw IllegalArgumentException(
            "不支持的教务适配器：${provider.adapterId.ifBlank { "未配置" }}",
        )
    }
}
