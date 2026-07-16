package dev.shusenzheng64.courseaxis

import dev.shusenzheng64.courseaxis.importer.AcademicImportAdapterRegistry
import dev.shusenzheng64.courseaxis.importer.AcademicImportOptions
import dev.shusenzheng64.courseaxis.importer.AcademicProvider
import dev.shusenzheng64.courseaxis.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AcademicImportAdapterTest {
    private fun provider(id: String, school: String, origin: String, namespace: String) = AcademicProvider(
        id = id,
        schoolName = school,
        vendor = "ZHENGFANG",
        allowedOrigin = origin,
        pathPrefix = "/jwglxt/",
        loginPath = "/jwglxt/xtgl/login_slogin.html",
        homePath = "/jwglxt/xtgl/index_initMenu.html",
        requiresThirdPartyCookies = false,
        semesterCodes = mapOf("first" to "3", "second" to "12"),
        adapterId = "zhengfang-v1",
        sessionNamespace = namespace,
    )

    private val providerA = provider(
        id = "synthetic-school-a",
        school = "合成大学 A",
        origin = "https://jw.synthetic-a.example",
        namespace = "11111111-1111-4111-8111-111111111111",
    )

    private val providerB = provider(
        id = "synthetic-school-b",
        school = "合成大学 B",
        origin = "https://jw.synthetic-b.example",
        namespace = "22222222-2222-4222-8222-222222222222",
    )

    private val raw = """{
        "ok":true,
        "term":{"xnm":"2026","xqm":"3"},
        "sources":{"courses":{"ok":true,"count":1},"exams":{"ok":true,"count":1},"sectionTimes":{"ok":true,"count":2}},
        "sectionTimes":[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:45","end":"09:25"}],
        "schedule":{"kbList":[{"jxb_id":"CLASS-A","kcmc":"合成课程","xm":"合成教师","xqj":"2","jc":"1-2节","zcd":"1-16周","cdmc":"合成楼 101"}],"sjkList":[]},
        "exams":{"items":[{"ks_id":"EXAM-A","kcmc":"合成课程","ksmc":"期末考试","kssj":"2027-01-05 09:00-11:00","cdmc":"合成楼 201"}]}
    }""".trimIndent()

    private fun options(provider: AcademicProvider) = AcademicImportOptions(
        provider = provider,
        academicYear = "2026",
        semesterCode = "3",
        termName = "2026-2027 第一学期",
        firstWeekMonday = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
        campus = "主校区",
    )

    @Test
    fun registryParsesSameVendorWithoutCrossProviderIdentityCollisions() {
        val adapter = AcademicImportAdapterRegistry.adapter(providerA)
        val first = adapter.parse(raw, options(providerA))
        val second = AcademicImportAdapterRegistry.adapter(providerB).parse(raw, options(providerB))

        assertEquals("zhengfang-v1", adapter.id)
        assertEquals("adapters/zhengfang-v1/import.js", adapter.scriptResource)
        assertEquals(providerA.id, first.term.providerId)
        assertEquals(providerB.id, second.term.providerId)
        assertNotEquals(first.term.id, second.term.id)
        assertNotEquals(first.events.first { it.kind == EventKind.COURSE }.id, second.events.first { it.kind == EventKind.COURSE }.id)
        assertNotEquals(first.events.first { it.kind == EventKind.EXAM }.id, second.events.first { it.kind == EventKind.EXAM }.id)
        assertEquals("course-7a087308e6a00825", first.events.first { it.kind == EventKind.COURSE }.id)
        assertEquals("exam-6ecab76fecd79cbc", first.events.first { it.kind == EventKind.EXAM }.id)
    }

    @Test
    fun invocationInjectsOnlySelectedProviderBoundaryAndDiscardsAsyncResult() {
        val invocation = AcademicImportAdapterRegistry.adapter(providerA).makeInvocation(
            provider = providerA,
            academicYear = "2026",
            semesterCode = "3",
            script = "(async function(){ return 1; })();",
        )

        assertTrue(invocation.contains("\"allowedOrigin\":\"https://jw.synthetic-a.example\""))
        assertTrue(invocation.contains("\"pathPrefix\":\"/jwglxt/\""))
        assertTrue(invocation.contains("\"xnm\":\"2026\""))
        assertTrue(invocation.contains("\"xqm\":\"3\""))
        assertTrue(invocation.endsWith("void 0;"))
    }

    @Test
    fun registryRejectsUnknownAdapterWithoutFallback() {
        val unknown = providerA.copy(adapterId = "unknown-v9")
        val error = assertThrows(IllegalArgumentException::class.java) {
            AcademicImportAdapterRegistry.adapter(unknown)
        }
        assertTrue(error.message.orEmpty().contains("unknown-v9"))
    }
}
