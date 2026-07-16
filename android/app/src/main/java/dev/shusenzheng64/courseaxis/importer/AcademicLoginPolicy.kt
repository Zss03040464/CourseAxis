package dev.shusenzheng64.courseaxis.importer

import org.json.JSONObject
import org.json.JSONTokener

data class AcademicCredentials(
    val username: String,
    val password: String,
)

data class AcademicPageSnapshot(
    val isLoginPage: Boolean,
    val captchaVisible: Boolean,
) {
    companion object {
        fun fromEvaluationResult(raw: String?): AcademicPageSnapshot {
            val fallback = AcademicPageSnapshot(isLoginPage = false, captchaVisible = false)
            if (raw.isNullOrBlank() || raw == "null") return fallback
            return runCatching {
                val decoded = JSONTokener(raw).nextValue()
                val value = when (decoded) {
                    is JSONObject -> decoded
                    is String -> JSONObject(decoded)
                    else -> return fallback
                }
                AcademicPageSnapshot(
                    isLoginPage = value.optBoolean("loginPage", false),
                    captchaVisible = value.optBoolean("captchaVisible", false),
                )
            }.getOrDefault(fallback)
        }
    }
}

enum class AcademicLoginAction {
    CONNECTED,
    NEED_CREDENTIALS,
    FILL_AND_SUBMIT,
    FILL_AND_WAIT_CAPTCHA,
    STOPPED,
}

object AcademicLoginPolicy {
    const val MAX_AUTOMATIC_ATTEMPTS = 1

    fun decide(
        page: AcademicPageSnapshot,
        hasCredentials: Boolean,
        automaticAttempts: Int,
    ): AcademicLoginAction = when {
        !page.isLoginPage -> AcademicLoginAction.CONNECTED
        !hasCredentials -> AcademicLoginAction.NEED_CREDENTIALS
        page.captchaVisible -> AcademicLoginAction.FILL_AND_WAIT_CAPTCHA
        automaticAttempts >= MAX_AUTOMATIC_ATTEMPTS -> AcademicLoginAction.STOPPED
        else -> AcademicLoginAction.FILL_AND_SUBMIT
    }
}
