package dev.shusenzheng64.courseaxis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shusenzheng64.courseaxis.data.SecureSyncSettings
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSyncSettingsTest {
    private val suffix = System.nanoTime().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferencesName = "courseaxis-test-sync-$suffix"
    private val keyAlias = "courseaxis-test-sync-key-$suffix"
    private val store = SecureSyncSettings(
        context = context,
        keyAlias = keyAlias,
        preferencesName = preferencesName,
    )

    @After
    fun cleanup() {
        store.clear()
    }

    @Test
    fun migratesLegacyPlaintextOnceAndKeepsOnlyEncryptedToken() {
        val legacyToken = "legacy-token-value-123456789"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        preferences.edit()
            .putString("serverUrl", "https://sync.example.test/")
            .putString("token", "  $legacyToken  ")
            .putLong("revision", 7)
            .commit()

        val migrated = store.load()

        assertEquals("https://sync.example.test/", migrated.serverUrl)
        assertEquals(legacyToken, migrated.token)
        assertEquals(7, migrated.revision)
        assertFalse(preferences.contains("token"))
        assertTrue(preferences.contains(SecureSyncSettings.KEY_TOKEN_CIPHERTEXT))
        assertFalse(preferences.all.values.joinToString("|").contains(legacyToken))
        assertEquals(migrated, store.load())
    }

    @Test
    fun saveRoundTripsAndClearRemovesSettingsWithoutPlaintext() {
        val token = "new-token-value-123456789012"

        store.save(" https://sync.example.test/path/ ", "  $token  ")
        store.setRevision(11)

        assertEquals(
            "https://sync.example.test/path",
            store.load().serverUrl,
        )
        assertEquals(token, store.load().token)
        assertEquals(11, store.load().revision)
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        assertFalse(preferences.contains("token"))
        assertFalse(preferences.all.values.joinToString("|").contains(token))

        store.clear()

        assertTrue(preferences.all.isEmpty())
        assertEquals("", store.load().token)
    }

    @Test
    fun corruptedCiphertextIsDiscardedWithoutPlaintextFallback() {
        store.save("https://sync.example.test", "token-value-123456789012345")
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(SecureSyncSettings.KEY_TOKEN_CIPHERTEXT, "not-valid-base64")
            .putString("token", "fallback-must-not-be-used")
            .commit()

        val loaded = store.load()

        assertEquals("", loaded.token)
        assertFalse(preferences.contains("token"))
        assertFalse(preferences.contains(SecureSyncSettings.KEY_TOKEN_CIPHERTEXT))
        assertEquals("https://sync.example.test", loaded.serverUrl)
    }

    @Test
    fun missingKeystoreKeyClearsCiphertextAndRequiresReconfiguration() {
        store.save("https://sync.example.test", "token-value-123456789012345")
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(keyAlias)
        }

        val loaded = store.load()
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

        assertEquals("", loaded.token)
        assertFalse(preferences.contains(SecureSyncSettings.KEY_TOKEN_CIPHERTEXT))
        assertFalse(preferences.contains("token"))
    }
}
