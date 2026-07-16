package dev.shusenzheng64.courseaxis

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shusenzheng64.courseaxis.importer.AcademicCredentials
import dev.shusenzheng64.courseaxis.importer.AcademicImportMessageChannel
import dev.shusenzheng64.courseaxis.importer.AcademicWebDataCleaner
import dev.shusenzheng64.courseaxis.importer.AcademicLoginScript
import dev.shusenzheng64.courseaxis.importer.AcademicWebViewPolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcademicLoginWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun secureConfigurationDisablesCleartextThirdPartyCookiesAndLocalFileAccess() {
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context)
            AcademicWebViewPolicy.configure(webView, AcademicWebViewPolicy.nfuProvider)
        }

        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
        assertEquals(false, webView.settings.allowFileAccess)
        assertEquals(false, webView.settings.allowContentAccess)
        assertEquals(false, CookieManager.getInstance().acceptThirdPartyCookies(webView))
        instrumentation.runOnMainSync { webView.destroy() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun importMessageChannelAcceptsTheProviderMainFrameOnce() {
        val loaded = CountDownLatch(1)
        val received = CountDownLatch(1)
        val count = AtomicInteger(0)
        var payload: String? = null
        lateinit var webView: WebView
        lateinit var channel: AcademicImportMessageChannel
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                AcademicWebViewPolicy.configure(this, AcademicWebViewPolicy.nfuProvider)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html",
                    "<html><body>ready</body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            channel = AcademicImportMessageChannel(AcademicWebViewPolicy.nfuProvider) {
                payload = it
                count.incrementAndGet()
                received.countDown()
            }
        }
        assertTrue("provider page did not finish loading", loaded.await(10, TimeUnit.SECONDS))

        val importPageLoaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            assertTrue(channel.install(webView))
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    importPageLoaded.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                "https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html",
                "<html><script>CourseAxisBridge.postMessage('once')</script></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("message was not delivered", received.await(10, TimeUnit.SECONDS))
        assertTrue("import page did not finish loading", importPageLoaded.await(10, TimeUnit.SECONDS))
        assertEquals("once", payload)
        assertEquals(1, count.get())
        assertEquals(false, channel.active)

        evaluate(webView, "CourseAxisBridge.postMessage('twice')")
        Thread.sleep(300)
        assertEquals(1, count.get())
        instrumentation.runOnMainSync { webView.destroy() }
    }

    @Test
    fun clearingAcademicWebDataRemovesCookiesAndResetsTheWebView() {
        val cookieSet = CountDownLatch(1)
        val cleared = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context)
            CookieManager.getInstance().setCookie(
                "https://jwxt.nfu.edu.cn",
                "JSESSIONID=courseaxis-test; Secure; Path=/",
            ) { cookieSet.countDown() }
        }
        assertTrue("test cookie was not stored", cookieSet.await(10, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            AcademicWebDataCleaner.clear(webView) { cleared.countDown() }
        }

        assertTrue("academic WebView data was not cleared", cleared.await(10, TimeUnit.SECONDS))
        assertFalse(
            CookieManager.getInstance().getCookie("https://jwxt.nfu.edu.cn")
                .orEmpty()
                .contains("JSESSIONID=courseaxis-test"),
        )
        instrumentation.runOnMainSync { webView.destroy() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun fillsTheRealFieldIdsAndWaitsForCaptchaBeforeSubmitting() {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://jwxt.nfu.edu.cn/jwglxt/xtgl/login_slogin.html",
                    """
                    <html><body>
                      <input id="yhm"><input id="mm"><div id="yzmDiv"><input id="yzm"></div>
                      <button id="dl" type="button" onclick="window.clicked += 1">登录</button>
                      <script>window.clicked = 0;</script>
                    </body></html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        }
        assertTrue("test login page did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        val credentials = AcademicCredentials(username = "a\"b", password = "p\\q")

        evaluate(webView, AcademicLoginScript.fill(credentials, submit = false))

        assertEquals("a\"b|p\\q|0", decodedString(evaluate(webView, fieldSnapshotScript())))

        evaluate(webView, AcademicLoginScript.fill(credentials, submit = true))

        assertEquals("a\"b|p\\q|1", decodedString(evaluate(webView, fieldSnapshotScript())))
        instrumentation.runOnMainSync { webView.destroy() }
    }

    private fun fieldSnapshotScript(): String =
        "document.querySelector('#yhm').value + '|' + " +
            "document.querySelector('#mm').value + '|' + window.clicked"

    private fun evaluate(webView: WebView, script: String): String {
        val result = arrayOfNulls<String>(1)
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                result[0] = it
                completed.countDown()
            }
        }
        assertTrue("JavaScript evaluation timed out", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result[0])
    }

    private fun decodedString(raw: String): String =
        JSONObject("{\"value\":$raw}").getString("value")
}
