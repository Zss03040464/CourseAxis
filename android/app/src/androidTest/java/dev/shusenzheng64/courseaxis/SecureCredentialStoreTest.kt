package dev.shusenzheng64.courseaxis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shusenzheng64.courseaxis.data.CredentialProviderRegistry
import dev.shusenzheng64.courseaxis.data.SecureCredentialStore
import dev.shusenzheng64.courseaxis.importer.AcademicCredentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCredentialStoreTest {
    private val suffix = System.nanoTime().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferencesName = "courseaxis-test-preferences-$suffix"
    private val store = SecureCredentialStore(
        context = context,
        keyAlias = "courseaxis-test-key-$suffix",
        preferencesName = preferencesName,
    )

    @After
    fun cleanup() {
        store.clear()
    }

    @Test
    fun credentialsRoundTripAndClearWithoutPlaintextPreferences() {
        val credentials = AcademicCredentials(username = "20250001", password = "secret-value")

        store.save(credentials)

        assertEquals(credentials, store.load())
        val storedValues = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .all.values.joinToString(separator = "|")
        assertFalse(storedValues.contains(credentials.username))
        assertFalse(storedValues.contains(credentials.password))

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun corruptedCiphertextIsDiscardedWithoutCrashing() {
        store.save(AcademicCredentials(username = "20250001", password = "secret-value"))
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        preferences.edit().putString("ciphertext", "not-valid-base64").commit()

        assertNull(store.load())
        assertFalse(preferences.contains("ciphertext"))
    }

    @Test
    fun legacyCredentialsMigrateOnceIntoProviderNamespace() {
        val legacyPreferences = "courseaxis-test-legacy-$suffix"
        val legacyKeyAlias = "courseaxis-test-legacy-key-$suffix"
        val migratedPreferences = "courseaxis-test-provider-$suffix"
        val migratedKeyAlias = "courseaxis-test-provider-key-$suffix"
        val legacy = SecureCredentialStore(
            context = context,
            keyAlias = legacyKeyAlias,
            preferencesName = legacyPreferences,
        )
        val migrated = SecureCredentialStore(
            context = context,
            keyAlias = migratedKeyAlias,
            preferencesName = migratedPreferences,
            legacyKeyAlias = legacyKeyAlias,
            legacyPreferencesName = legacyPreferences,
        )
        val credentials = AcademicCredentials("synthetic-user", "synthetic-password")

        try {
            legacy.save(credentials)

            assertEquals(credentials, migrated.load())
            assertFalse(
                context.getSharedPreferences(legacyPreferences, Context.MODE_PRIVATE)
                    .contains("ciphertext"),
            )
            assertEquals(credentials, migrated.load())
        } finally {
            migrated.clear()
            legacy.clear()
        }
    }

    @Test
    fun productionProviderNamespacesRemainDiscoverableForFutureFullDeletion() {
        val providerId = "synthetic-provider-${suffix.filter(Char::isDigit)}"
        val providerStore = SecureCredentialStore(context, providerId)

        try {
            providerStore.save(AcademicCredentials("synthetic-user", "synthetic-password"))

            assertEquals(true, providerId in CredentialProviderRegistry.providerIds(context))
        } finally {
            providerStore.clear()
            CredentialProviderRegistry.clear(context)
        }
    }
}
