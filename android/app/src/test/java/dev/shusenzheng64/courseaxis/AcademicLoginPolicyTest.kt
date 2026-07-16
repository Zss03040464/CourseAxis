package dev.shusenzheng64.courseaxis

import dev.shusenzheng64.courseaxis.data.SecureCredentialStore
import dev.shusenzheng64.courseaxis.importer.AcademicCredentials
import dev.shusenzheng64.courseaxis.importer.AcademicNavigationDestination
import dev.shusenzheng64.courseaxis.importer.AcademicLoginAction
import dev.shusenzheng64.courseaxis.importer.AcademicLoginPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicLoginScript
import dev.shusenzheng64.courseaxis.importer.AcademicPageSnapshot
import dev.shusenzheng64.courseaxis.importer.AcademicWebViewPolicy
import dev.shusenzheng64.courseaxis.importer.ImportWorkspacePolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicLoginPolicyTest {
    @Test
    fun webViewPolicyEmbedsOnlyTheExactProviderAndBlocksUnsafeSchemes() {
        val provider = AcademicWebViewPolicy.nfuProvider

        assertEquals(
            AcademicNavigationDestination.EMBEDDED,
            AcademicWebViewPolicy.destination("https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html", provider),
        )
        listOf(
            "https://portal.nfu.edu.cn/jwglxt/",
            "https://jwxt.nfu.edu.cn/",
            "https://example.com/",
            "http://jwxt.nfu.edu.cn/jwglxt/",
        ).forEach { url ->
            assertEquals(url, AcademicNavigationDestination.EXTERNAL_BROWSER, AcademicWebViewPolicy.destination(url, provider))
        }
        listOf("javascript:alert(1)", "file:///etc/passwd", "content://settings/system").forEach { url ->
            assertEquals(url, AcademicNavigationDestination.BLOCKED, AcademicWebViewPolicy.destination(url, provider))
        }
        assertTrue(AcademicWebViewPolicy.canEvaluateImport("https://jwxt.nfu.edu.cn/jwglxt/", provider))
        assertFalse(AcademicWebViewPolicy.canEvaluateImport("https://portal.nfu.edu.cn/jwglxt/", provider))
        assertFalse(AcademicWebViewPolicy.acceptsThirdPartyCookies(provider))
        assertEquals(setOf("https://jwxt.nfu.edu.cn"), AcademicWebViewPolicy.allowedOriginRules(provider))
    }

    @Test
    fun loginPolicySubmitsOnlyWithoutCaptchaAndStopsAfterOneAttempt() {
        val login = AcademicPageSnapshot(isLoginPage = true, captchaVisible = false)

        assertEquals(
            AcademicLoginAction.FILL_AND_SUBMIT,
            AcademicLoginPolicy.decide(login, hasCredentials = true, automaticAttempts = 0),
        )
        assertEquals(
            AcademicLoginAction.STOPPED,
            AcademicLoginPolicy.decide(login, hasCredentials = true, automaticAttempts = 1),
        )
        assertEquals(
            AcademicLoginAction.FILL_AND_WAIT_CAPTCHA,
            AcademicLoginPolicy.decide(
                login.copy(captchaVisible = true),
                hasCredentials = true,
                automaticAttempts = 0,
            ),
        )
        assertEquals(
            AcademicLoginAction.NEED_CREDENTIALS,
            AcademicLoginPolicy.decide(login, hasCredentials = false, automaticAttempts = 0),
        )
        assertEquals(
            AcademicLoginAction.CONNECTED,
            AcademicLoginPolicy.decide(
                AcademicPageSnapshot(isLoginPage = false, captchaVisible = false),
                hasCredentials = true,
                automaticAttempts = 1,
            ),
        )
    }

    @Test
    fun pageSnapshotDecodesWebViewEvaluationResult() {
        val encoded = JSONObject.quote("{\"loginPage\":true,\"captchaVisible\":true}")

        assertEquals(
            AcademicPageSnapshot(isLoginPage = true, captchaVisible = true),
            AcademicPageSnapshot.fromEvaluationResult(encoded),
        )
        assertEquals(
            AcademicPageSnapshot(isLoginPage = false, captchaVisible = false),
            AcademicPageSnapshot.fromEvaluationResult("null"),
        )
    }

    @Test
    fun loginScriptJsonQuotesCredentialsAndUsesTheSchoolLoginButton() {
        val script = AcademicLoginScript.fill(
            AcademicCredentials(username = "a\"b", password = "p\\q\n"),
            submit = true,
        )

        assertTrue(script.contains(JSONObject.quote("a\"b")))
        assertTrue(script.contains(JSONObject.quote("p\\q\n")))
        assertTrue(script.contains("button.click()"))
        assertFalse(script.contains("const username='"))
        assertFalse(script.contains("fetch("))
    }

    @Test
    fun savedUsernameIsMaskedWithoutRevealingTheMiddle() {
        assertEquals("20••••01", SecureCredentialStore.maskUsername("20250001"))
        assertEquals("a•c", SecureCredentialStore.maskUsername("abc"))
        assertEquals("•", SecureCredentialStore.maskUsername("x"))
        assertEquals("", SecureCredentialStore.maskUsername(""))
    }

    @Test
    fun importWorkspaceKeepsOneReadActionAndNoDuplicateTermFields() {
        assertEquals("读取课程、考试与真实作息", ImportWorkspacePolicy.primaryActionLabel)
        assertEquals("保存账号", ImportWorkspacePolicy.credentialSaveLabel)
        assertFalse(ImportWorkspacePolicy.showsLegacyQuickActions)
        assertFalse(ImportWorkspacePolicy.showsTermFields)
    }

    @Test
    fun importWorkspaceKeepsTheSavedPasswordWhenOnlyTheUsernameChanges() {
        val saved = AcademicCredentials("old", "saved-password")

        assertEquals(
            AcademicCredentials("new", "saved-password"),
            ImportWorkspacePolicy.credentialsForSave(" new ", "", saved),
        )
        assertEquals(
            AcademicCredentials("new", "new-password"),
            ImportWorkspacePolicy.credentialsForSave("new", "new-password", saved),
        )
        assertEquals(
            "请填写教务用户名和密码",
            runCatching { ImportWorkspacePolicy.credentialsForSave("new", "", null) }
                .exceptionOrNull()?.message,
        )
    }
}
