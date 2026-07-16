package dev.shusenzheng64.courseaxis.importer

import dev.shusenzheng64.courseaxis.core.SemesterOption
import dev.shusenzheng64.courseaxis.model.Term

data class AcademicProviderSelection(
    val provider: AcademicProvider?,
    val term: Term?,
)

object AcademicProviderSelectionPolicy {
    fun resolve(
        providers: List<AcademicProvider>,
        requestedProviderId: String?,
        terms: List<Term>,
        activeTermId: String?,
    ): AcademicProviderSelection {
        val provider = providers.firstOrNull { it.id == requestedProviderId }
            ?: providers.firstOrNull()
        if (provider == null) return AcademicProviderSelection(provider = null, term = null)

        val providerTerms = terms.filter { it.providerId == provider.id }
        val active = providerTerms.firstOrNull { it.id == activeTermId }
        return AcademicProviderSelection(
            provider = provider,
            term = active ?: providerTerms.maxByOrNull { it.updatedAt },
        )
    }
}

object AcademicProviderDisplayPolicy {
    fun labels(providers: List<AcademicProvider>): Map<String, String> {
        val schoolCounts = providers.groupingBy { it.schoolName }.eachCount()
        return providers.associate { provider ->
            provider.id to if (schoolCounts.getValue(provider.schoolName) == 1) {
                provider.schoolName
            } else {
                "${provider.schoolName} · ${provider.vendor} · ${provider.id}"
            }
        }
    }
}

object AcademicProviderSemesterPolicy {
    fun code(provider: AcademicProvider, semester: SemesterOption): String =
        provider.semesterCodes[semester.key]
            ?: throw IllegalArgumentException(
                "${provider.schoolName}未配置${semester.label}代码",
            )

    fun option(provider: AcademicProvider, code: String): SemesterOption? =
        SemesterOption.entries.firstOrNull { semester ->
            provider.semesterCodes[semester.key] == code.trim()
        }

    fun options(provider: AcademicProvider): List<SemesterOption> =
        SemesterOption.entries.filter { it.key in provider.semesterCodes }

    private val SemesterOption.key: String
        get() = when (this) {
            SemesterOption.FIRST -> "first"
            SemesterOption.SECOND -> "second"
            SemesterOption.THIRD -> "third"
        }
}
