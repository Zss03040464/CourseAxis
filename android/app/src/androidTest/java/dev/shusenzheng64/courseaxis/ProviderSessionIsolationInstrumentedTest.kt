package dev.shusenzheng64.courseaxis

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import dev.shusenzheng64.courseaxis.importer.AcademicProvider
import dev.shusenzheng64.courseaxis.importer.AcademicWebDataCleaner
import dev.shusenzheng64.courseaxis.importer.AcademicWebProfilePolicy
import dev.shusenzheng64.courseaxis.importer.AcademicWebSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ProviderSessionIsolationInstrumentedTest {
    private fun provider(id: String, namespace: String, origin: String) = AcademicProvider(
        id = id,
        schoolName = "合成大学",
        vendor = "ZHENGFANG",
        allowedOrigin = origin,
        pathPrefix = "/jwglxt/",
        loginPath = "/jwglxt/login",
        homePath = "/jwglxt/home",
        requiresThirdPartyCookies = false,
        adapterId = "zhengfang-v1",
        sessionNamespace = namespace,
    )

    @Test
    fun providerProfilesDoNotShareCookiesOrRequireUnsafeFallback() {
        val first = provider(
            "synthetic-profile-a",
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "https://a.synthetic.invalid",
        )
        val second = provider(
            "synthetic-profile-b",
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            "https://b.synthetic.invalid",
        )

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val plan = AcademicWebProfilePolicy.plan(
                provider = second,
                multiProfileSupported = false,
                lastDefaultProfileProviderId = first.id,
            )
            assertTrue(plan.requiresDefaultProfileClear)
            assertFalse(plan.usesIsolatedProfile)
            return
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var firstView: WebView
        lateinit var secondView: WebView
        lateinit var firstBinding: dev.shusenzheng64.courseaxis.importer.AcademicWebSessionBinding
        lateinit var secondBinding: dev.shusenzheng64.courseaxis.importer.AcademicWebSessionBinding
        instrumentation.runOnMainSync {
            firstView = WebView(context)
            secondView = WebView(context)
            firstBinding = AcademicWebSession.bind(firstView, first, lastDefaultProfileProviderId = null)
            secondBinding = AcademicWebSession.bind(secondView, second, lastDefaultProfileProviderId = null)
            firstBinding.cookieManager.setCookie(first.allowedOrigin, "courseaxis_a=one")
            secondBinding.cookieManager.setCookie(second.allowedOrigin, "courseaxis_b=two")
        }
        instrumentation.waitForIdleSync()

        try {
            assertTrue(firstBinding.cookieManager.getCookie(first.allowedOrigin).orEmpty().contains("courseaxis_a=one"))
            assertFalse(secondBinding.cookieManager.getCookie(first.allowedOrigin).orEmpty().contains("courseaxis_a=one"))
            assertTrue(secondBinding.cookieManager.getCookie(second.allowedOrigin).orEmpty().contains("courseaxis_b=two"))
            assertFalse(firstBinding.cookieManager.getCookie(second.allowedOrigin).orEmpty().contains("courseaxis_b=two"))
        } finally {
            val cleanup = CountDownLatch(1)
            val cleanupFailure = AtomicReference<Throwable?>(null)
            instrumentation.runOnMainSync {
                firstView.destroy()
                secondView.destroy()
                AcademicWebDataCleaner.clearAllProviders(
                    providers = listOf(first),
                    onComplete = { cleanup.countDown() },
                    onFailure = {
                        cleanupFailure.set(it)
                        cleanup.countDown()
                    },
                )
            }
            assertTrue(cleanup.await(10, TimeUnit.SECONDS))
            assertNull(cleanupFailure.get())
            instrumentation.runOnMainSync {
                val store = ProfileStore.getInstance()
                store.getProfile(firstBinding.profileName)?.let { profile ->
                    assertFalse(profile.cookieManager.getCookie(first.allowedOrigin).orEmpty().contains("courseaxis_a=one"))
                }
                store.getProfile(secondBinding.profileName)?.let { profile ->
                    assertFalse(profile.cookieManager.getCookie(second.allowedOrigin).orEmpty().contains("courseaxis_b=two"))
                }
            }
        }
    }
}
