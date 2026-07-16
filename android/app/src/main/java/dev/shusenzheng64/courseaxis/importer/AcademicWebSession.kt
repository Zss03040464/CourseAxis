package dev.shusenzheng64.courseaxis.importer

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

data class AcademicWebProfilePlan(
    val profileName: String,
    val usesIsolatedProfile: Boolean,
    val requiresDefaultProfileClear: Boolean,
)

object AcademicWebProfilePolicy {
    fun name(provider: AcademicProvider): String {
        val namespace = runCatching { UUID.fromString(provider.sessionNamespace).toString() }
            .getOrElse { throw IllegalArgumentException("Provider 会话命名空间无效：${provider.id}") }
        return "courseaxis-$namespace"
    }

    fun plan(
        provider: AcademicProvider,
        multiProfileSupported: Boolean,
        lastDefaultProfileProviderId: String?,
    ): AcademicWebProfilePlan {
        val previousProviderId = lastDefaultProfileProviderId ?: LEGACY_DEFAULT_PROVIDER_ID
        return AcademicWebProfilePlan(
            profileName = name(provider),
            usesIsolatedProfile = multiProfileSupported,
            requiresDefaultProfileClear = !multiProfileSupported &&
                previousProviderId != provider.id,
        )
    }

    private const val LEGACY_DEFAULT_PROVIDER_ID = "nfu-zhengfang"
}

data class AcademicWebSessionBinding(
    val providerId: String,
    val profileName: String,
    val usesIsolatedProfile: Boolean,
    val requiresDefaultProfileClear: Boolean,
    val cookieManager: CookieManager,
    val webStorage: WebStorage,
)

object AcademicWebSession {
    fun bind(
        webView: WebView,
        provider: AcademicProvider,
        lastDefaultProfileProviderId: String?,
    ): AcademicWebSessionBinding {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        val plan = AcademicWebProfilePolicy.plan(provider, supported, lastDefaultProfileProviderId)
        if (supported) {
            val profile = ProfileStore.getInstance().getOrCreateProfile(plan.profileName)
            WebViewCompat.setProfile(webView, plan.profileName)
            return AcademicWebSessionBinding(
                providerId = provider.id,
                profileName = plan.profileName,
                usesIsolatedProfile = true,
                requiresDefaultProfileClear = false,
                cookieManager = profile.cookieManager,
                webStorage = profile.webStorage,
            )
        }
        return AcademicWebSessionBinding(
            providerId = provider.id,
            profileName = plan.profileName,
            usesIsolatedProfile = false,
            requiresDefaultProfileClear = plan.requiresDefaultProfileClear,
            cookieManager = CookieManager.getInstance(),
            webStorage = WebStorage.getInstance(),
        )
    }
}

class AcademicDefaultProfileState(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun lastProviderId(): String? = preferences.getString(KEY_PROVIDER_ID, null)

    fun markBound(binding: AcademicWebSessionBinding) {
        if (!binding.usesIsolatedProfile) {
            check(preferences.edit().putString(KEY_PROVIDER_ID, binding.providerId).commit()) {
                "无法保存默认 WebView Provider 状态"
            }
        }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) {
            "无法清除默认 WebView Provider 状态"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "courseaxis_academic_web_session"
        const val KEY_PROVIDER_ID = "default_profile_provider_id"
    }
}
