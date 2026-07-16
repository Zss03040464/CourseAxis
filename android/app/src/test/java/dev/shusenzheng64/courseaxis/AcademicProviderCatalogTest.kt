package dev.shusenzheng64.courseaxis

import dev.shusenzheng64.courseaxis.data.CredentialNamespace
import dev.shusenzheng64.courseaxis.core.SemesterOption
import dev.shusenzheng64.courseaxis.importer.AcademicProviderCatalog
import dev.shusenzheng64.courseaxis.importer.AcademicProviderDisplayPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicProviderSelectionPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicProviderSemesterPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicWebProfilePolicy
import dev.shusenzheng64.courseaxis.model.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicProviderCatalogTest {
    private val catalog = """{"schemaVersion":1,"providers":["synthetic-school-a"]}"""
    private val provider = """{
        "schemaVersion":1,
        "id":"synthetic-school-a",
        "schoolName":"合成大学 A",
        "vendor":"ZHENGFANG",
        "adapterId":"zhengfang-v1",
        "allowedOrigin":"https://jw.synthetic-a.example",
        "pathPrefix":"/jwglxt/",
        "loginPath":"/jwglxt/xtgl/login_slogin.html",
        "homePath":"/jwglxt/xtgl/index_initMenu.html",
        "requiresThirdPartyCookies":false,
        "sessionNamespace":"11111111-1111-4111-8111-111111111111",
        "semesterCodes":{"first":"3","second":"12"}
    }""".trimIndent()

    @Test
    fun bundledCatalogDecodesOnlyValidatedProviders() {
        val providers = AcademicProviderCatalog.decode(
            catalogJson = catalog,
            providerJsonById = mapOf("synthetic-school-a" to provider),
        )

        assertEquals(listOf("synthetic-school-a"), providers.map { it.id })
        assertEquals("zhengfang-v1", providers.single().adapterId)
        assertEquals("11111111-1111-4111-8111-111111111111", providers.single().sessionNamespace)
    }

    @Test
    fun catalogRejectsDuplicatesUnknownAdaptersAndInvalidNamespaces() {
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                catalog.replace(
                    "[\"synthetic-school-a\"]",
                    "[\"synthetic-school-a\",\"synthetic-school-a\"]",
                ),
                mapOf("synthetic-school-a" to provider),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                catalog,
                mapOf("synthetic-school-a" to provider.replace("zhengfang-v1", "unknown-v9")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                catalog,
                mapOf(
                    "synthetic-school-a" to provider.replace(
                        "11111111-1111-4111-8111-111111111111",
                        "not-a-uuid",
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                catalog.replace("synthetic-school-a", "../unsafe"),
                mapOf("../unsafe" to provider.replace("synthetic-school-a", "../unsafe")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                catalog,
                mapOf(
                    "synthetic-school-a" to provider.replace(
                        "\"first\":\"3\",\"second\":\"12\"",
                        "\"autumn\":\"3\"",
                    ),
                ),
            )
        }
        val secondProvider = provider
            .replace("synthetic-school-a", "synthetic-school-b")
            .replace("合成大学 A", "合成大学 B")
            .replace("jw.synthetic-a.example", "jw.synthetic-b.example")
        assertThrows(IllegalArgumentException::class.java) {
            AcademicProviderCatalog.decode(
                """{"schemaVersion":1,"providers":["synthetic-school-a","synthetic-school-b"]}""",
                mapOf(
                    "synthetic-school-a" to provider,
                    "synthetic-school-b" to secondProvider,
                ),
            )
        }
    }

    @Test
    fun profileAndCredentialNamespacesAreStableAndProviderScoped() {
        val selected = AcademicProviderCatalog.decode(
            catalog,
            mapOf("synthetic-school-a" to provider),
        ).single()
        val other = selected.copy(
            id = "synthetic-school-b",
            sessionNamespace = "22222222-2222-4222-8222-222222222222",
        )

        assertEquals(
            "courseaxis-11111111-1111-4111-8111-111111111111",
            AcademicWebProfilePolicy.name(selected),
        )
        assertNotEquals(AcademicWebProfilePolicy.name(selected), AcademicWebProfilePolicy.name(other))
        assertNotEquals(
            CredentialNamespace.forProvider(selected.id),
            CredentialNamespace.forProvider(other.id),
        )
        assertEquals(
            "courseaxis_credentials_synthetic-school-a",
            CredentialNamespace.forProvider(selected.id).preferencesName,
        )
        assertEquals(
            "dev.shusenzheng64.courseaxis.academic-login.synthetic-school-a",
            CredentialNamespace.forProvider(selected.id).keyAlias,
        )
        assertThrows(IllegalArgumentException::class.java) {
            CredentialNamespace.forProvider("../unsafe")
        }
    }

    @Test
    fun unsupportedMultiProfileRequiresClearOnlyWhenProviderChanges() {
        val selected = AcademicProviderCatalog.decode(
            catalog,
            mapOf("synthetic-school-a" to provider),
        ).single()

        val first = AcademicWebProfilePolicy.plan(
            provider = selected,
            multiProfileSupported = false,
            lastDefaultProfileProviderId = null,
        )
        val legacyFirst = AcademicWebProfilePolicy.plan(
            provider = selected.copy(id = "nfu-zhengfang"),
            multiProfileSupported = false,
            lastDefaultProfileProviderId = null,
        )
        val repeated = AcademicWebProfilePolicy.plan(
            provider = selected,
            multiProfileSupported = false,
            lastDefaultProfileProviderId = selected.id,
        )
        val switched = AcademicWebProfilePolicy.plan(
            provider = selected,
            multiProfileSupported = false,
            lastDefaultProfileProviderId = "synthetic-school-b",
        )
        val isolated = AcademicWebProfilePolicy.plan(
            provider = selected,
            multiProfileSupported = true,
            lastDefaultProfileProviderId = "synthetic-school-b",
        )

        assertTrue(first.requiresDefaultProfileClear)
        assertFalse(legacyFirst.requiresDefaultProfileClear)
        assertFalse(repeated.requiresDefaultProfileClear)
        assertTrue(switched.requiresDefaultProfileClear)
        assertTrue(isolated.usesIsolatedProfile)
        assertFalse(isolated.requiresDefaultProfileClear)
    }

    @Test
    fun providerSelectionNeverReusesAnotherSchoolsActiveTerm() {
        val first = AcademicProviderCatalog.decode(
            catalog,
            mapOf("synthetic-school-a" to provider),
        ).single()
        val second = first.copy(
            id = "synthetic-school-b",
            schoolName = "合成大学 B",
            sessionNamespace = "22222222-2222-4222-8222-222222222222",
        )
        val firstTerm = term("term-a", first.id, "2026-01-01T00:00:00Z")
        val secondTerm = term("term-b", second.id, "2027-01-01T00:00:00Z")

        val selection = AcademicProviderSelectionPolicy.resolve(
            providers = listOf(first, second),
            requestedProviderId = first.id,
            terms = listOf(firstTerm, secondTerm),
            activeTermId = secondTerm.id,
        )

        assertEquals(first.id, selection.provider?.id)
        assertEquals(firstTerm.id, selection.term?.id)

        val providerWithoutTerm = AcademicProviderSelectionPolicy.resolve(
            providers = listOf(first, second),
            requestedProviderId = second.id,
            terms = listOf(firstTerm),
            activeTermId = firstTerm.id,
        )
        assertEquals(second.id, providerWithoutTerm.provider?.id)
        assertEquals(null, providerWithoutTerm.term)
    }

    @Test
    fun semesterCodesAreResolvedFromTheSelectedProvider() {
        val selected = AcademicProviderCatalog.decode(
            catalog,
            mapOf("synthetic-school-a" to provider),
        ).single().copy(
            semesterCodes = mapOf("first" to "A", "second" to "B", "third" to "C"),
        )

        assertEquals("A", AcademicProviderSemesterPolicy.code(selected, SemesterOption.FIRST))
        assertEquals("B", AcademicProviderSemesterPolicy.code(selected, SemesterOption.SECOND))
        assertEquals(SemesterOption.THIRD, AcademicProviderSemesterPolicy.option(selected, "C"))
        assertEquals(null, AcademicProviderSemesterPolicy.option(selected, "unknown"))
    }

    @Test
    fun duplicateSchoolNamesStillHaveDistinctProviderLabels() {
        val selected = AcademicProviderCatalog.decode(
            catalog,
            mapOf("synthetic-school-a" to provider),
        ).single()
        val other = selected.copy(
            id = "synthetic-school-b",
            vendor = "OTHER_VENDOR",
            sessionNamespace = "22222222-2222-4222-8222-222222222222",
        )

        val labels = AcademicProviderDisplayPolicy.labels(listOf(selected, other))

        assertNotEquals(labels.getValue(selected.id), labels.getValue(other.id))
        assertTrue(labels.getValue(selected.id).contains(selected.id))
        assertTrue(labels.getValue(other.id).contains(other.id))
    }

    private fun term(id: String, providerId: String, updatedAt: String) = Term(
        id = id,
        name = id,
        firstWeekMonday = "2026-09-07",
        providerId = providerId,
        updatedAt = updatedAt,
    )
}
