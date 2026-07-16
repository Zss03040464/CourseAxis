package dev.shusenzheng64.courseaxis.importer

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI

enum class AcademicNavigationDestination {
    EMBEDDED,
    EXTERNAL_BROWSER,
    BLOCKED,
}

object AcademicWebViewPolicy {
    val nfuProvider = AcademicProvider(
        id = "nfu-zhengfang",
        schoolName = "广州南方学院",
        vendor = "ZHENGFANG",
        allowedOrigin = "https://jwxt.nfu.edu.cn",
        pathPrefix = "/jwglxt/",
        loginPath = "/jwglxt/xtgl/login_slogin.html",
        homePath = "/jwglxt/xtgl/index_initMenu.html",
        requiresThirdPartyCookies = false,
        semesterCodes = mapOf("first" to "3", "second" to "12", "third" to "16"),
        adapterId = "zhengfang-v1",
        sessionNamespace = "7c8cd13a-2094-5c1b-9d84-2ee62d7a4f70",
    )

    fun destination(url: String, provider: AcademicProvider): AcademicNavigationDestination {
        if (AcademicProviderPolicy.isAllowed(url, provider)) return AcademicNavigationDestination.EMBEDDED
        val candidate = runCatching { URI(url) }.getOrNull()
            ?: return AcademicNavigationDestination.BLOCKED
        return if (
            candidate.scheme?.lowercase() in setOf("http", "https") &&
            candidate.host != null &&
            candidate.userInfo == null
        ) {
            AcademicNavigationDestination.EXTERNAL_BROWSER
        } else {
            AcademicNavigationDestination.BLOCKED
        }
    }

    fun canEvaluateImport(url: String?, provider: AcademicProvider): Boolean =
        url != null && AcademicProviderPolicy.isAllowed(url, provider)

    fun acceptsThirdPartyCookies(provider: AcademicProvider): Boolean =
        provider.requiresThirdPartyCookies

    fun allowedOriginRules(provider: AcademicProvider): Set<String> =
        setOf(provider.allowedOrigin)

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        provider: AcademicProvider,
        cookieManager: CookieManager = CookieManager.getInstance(),
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        cookieManager.apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, acceptsThirdPartyCookies(provider))
        }
    }
}

class AcademicImportMessageChannel(
    private val provider: AcademicProvider,
    private val onMessage: (String) -> Unit,
) {
    var active: Boolean = false
        private set

    private var installedWebView: WebView? = null

    fun install(webView: WebView): Boolean {
        remove()
        if (!AcademicWebViewPolicy.canEvaluateImport(webView.url, provider)) return false
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false

        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            AcademicWebViewPolicy.allowedOriginRules(provider),
        ) { sourceView, message, sourceOrigin, isMainFrame, _ ->
            if (!active || sourceView !== installedWebView || !isMainFrame) return@addWebMessageListener
            if (!AcademicProviderPolicy.isAllowedOrigin(sourceOrigin.toString(), provider)) return@addWebMessageListener
            if (!AcademicWebViewPolicy.canEvaluateImport(sourceView.url, provider)) return@addWebMessageListener
            if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
            val payload = message.data ?: return@addWebMessageListener
            if (payload.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                remove()
                return@addWebMessageListener
            }
            remove()
            onMessage(payload)
        }
        installedWebView = webView
        active = true
        return true
    }

    fun remove() {
        val webView = installedWebView
        if (webView != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME) }
        }
        installedWebView = null
        active = false
    }

    private companion object {
        const val BRIDGE_NAME = "CourseAxisBridge"
        const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    }
}
