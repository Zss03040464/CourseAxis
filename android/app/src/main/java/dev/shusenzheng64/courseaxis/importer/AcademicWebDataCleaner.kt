package dev.shusenzheng64.courseaxis.importer

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature

object AcademicWebDataCleaner {
    fun clear(webView: WebView?, onComplete: () -> Unit = {}) {
        clear(webView, binding = null, onComplete = onComplete)
    }

    @Suppress("DEPRECATION")
    fun clear(
        webView: WebView?,
        binding: AcademicWebSessionBinding?,
        onComplete: () -> Unit = {},
    ) {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
        }
        val storage = binding?.webStorage ?: WebStorage.getInstance()
        val cookies = binding?.cookieManager ?: CookieManager.getInstance()
        if (binding?.usesIsolatedProfile != true) {
            webView?.context?.applicationContext?.let { context ->
                WebViewDatabase.getInstance(context).apply {
                    clearFormData()
                    clearHttpAuthUsernamePassword()
                }
            }
        }
        clearBrowsingData(storage, cookies, onComplete)
    }

    fun clearProvider(
        provider: AcademicProvider,
        fallbackWebView: WebView? = null,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            clear(webView = fallbackWebView, onComplete = onComplete)
            return
        }
        val profileName = AcademicWebProfilePolicy.name(provider)
        val store = runCatching { ProfileStore.getInstance() }.getOrElse {
            Handler(Looper.getMainLooper()).post { onFailure(it) }
            return
        }
        val profileNames = runCatching { store.allProfileNames }.getOrElse {
            Handler(Looper.getMainLooper()).post { onFailure(it) }
            return
        }
        if (profileName !in profileNames) {
            Handler(Looper.getMainLooper()).post(onComplete)
            return
        }
        clearProfile(store, profileName, onComplete, onFailure)
    }

    private fun clearProfile(
        store: ProfileStore,
        profileName: String,
        onComplete: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val deletion = runCatching { store.deleteProfile(profileName) }
        if (deletion.getOrNull() == true) {
            Handler(Looper.getMainLooper()).post(onComplete)
            return
        }
        if (deletion.isSuccess) {
            Handler(Looper.getMainLooper()).post(onComplete)
            return
        }
        val profile = runCatching { store.getProfile(profileName) }.getOrElse {
            Handler(Looper.getMainLooper()).post { onFailure(deletion.exceptionOrNull() ?: it) }
            return
        }
        if (profile == null) {
            Handler(Looper.getMainLooper()).post { onFailure(requireNotNull(deletion.exceptionOrNull())) }
            return
        }
        clearBrowsingData(profile.webStorage, profile.cookieManager, onComplete)
    }

    fun clearAllProviders(
        providers: List<AcademicProvider>,
        fallbackWebView: WebView? = null,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            clear(webView = fallbackWebView, onComplete = onComplete)
            return
        }
        val store = runCatching { ProfileStore.getInstance() }.getOrElse {
            Handler(Looper.getMainLooper()).post { onFailure(it) }
            return
        }
        val allProfileNames = runCatching { store.allProfileNames }.getOrElse {
            Handler(Looper.getMainLooper()).post { onFailure(it) }
            return
        }
        val configuredNames = providers.distinctBy { it.id }.map(AcademicWebProfilePolicy::name)
        val ownedNames = allProfileNames.filter { it.startsWith(COURSEAXIS_PROFILE_PREFIX) }
        val remaining = (configuredNames + ownedNames)
            .distinct()
            .filter { it in allProfileNames }
            .iterator()
        fun clearNext() {
            if (remaining.hasNext()) {
                clearProfile(
                    store = store,
                    profileName = remaining.next(),
                    onComplete = ::clearNext,
                    onFailure = onFailure,
                )
            } else {
                clear(webView = fallbackWebView, onComplete = onComplete)
            }
        }
        clearNext()
    }

    private fun clearBrowsingData(
        storage: WebStorage,
        cookies: CookieManager,
        onComplete: () -> Unit,
    ) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            val started = runCatching {
                WebStorageCompat.deleteBrowsingData(storage) {
                    Handler(Looper.getMainLooper()).post(onComplete)
                }
            }.isSuccess
            if (started) return
        }
        storage.deleteAllData()
        cookies.removeAllCookies {
            cookies.flush()
            Handler(Looper.getMainLooper()).post(onComplete)
        }
    }

    private const val COURSEAXIS_PROFILE_PREFIX = "courseaxis-"
}
