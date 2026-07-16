package dev.shusenzheng64.courseaxis.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.shusenzheng64.courseaxis.importer.AcademicCredentials
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AcademicCredentialNamespace(
    val preferencesName: String,
    val keyAlias: String,
)

object CredentialNamespace {
    val legacy = AcademicCredentialNamespace(
        preferencesName = SecureCredentialStore.PREFERENCES_NAME,
        keyAlias = SecureCredentialStore.DEFAULT_KEY_ALIAS,
    )

    fun forProvider(providerId: String): AcademicCredentialNamespace {
        require(SAFE_PROVIDER_ID.matches(providerId)) { "Provider ID 不安全：$providerId" }
        return AcademicCredentialNamespace(
            preferencesName = "courseaxis_credentials_$providerId",
            keyAlias = "dev.shusenzheng64.courseaxis.academic-login.$providerId",
        )
    }

    private val SAFE_PROVIDER_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
}

object CredentialProviderRegistry {
    fun register(context: Context, providerId: String) {
        CredentialNamespace.forProvider(providerId)
        val preferences = preferences(context)
        val providerIds = preferences.getStringSet(KEY_PROVIDER_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (providerIds.add(providerId)) {
            check(preferences.edit().putStringSet(KEY_PROVIDER_IDS, providerIds).commit()) {
                "无法保存教务 Provider 凭据索引"
            }
        }
    }

    fun providerIds(context: Context): Set<String> =
        preferences(context).getStringSet(KEY_PROVIDER_IDS, emptySet())
            .orEmpty()
            .filterTo(mutableSetOf()) { providerId ->
                runCatching { CredentialNamespace.forProvider(providerId) }.isSuccess
            }

    fun clear(context: Context) {
        check(preferences(context).edit().clear().commit()) {
            "无法清除教务 Provider 凭据索引"
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "courseaxis_credential_provider_registry"
    private const val KEY_PROVIDER_IDS = "provider_ids"
}

class SecureCredentialStore private constructor(
    context: Context,
    private val namespace: AcademicCredentialNamespace,
    private val legacyNamespace: AcademicCredentialNamespace?,
    registryProviderId: String?,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        namespace.preferencesName,
        Context.MODE_PRIVATE,
    )

    init {
        registryProviderId?.let { CredentialProviderRegistry.register(appContext, it) }
    }

    constructor(
        context: Context,
        providerId: String = DEFAULT_PROVIDER_ID,
    ) : this(
        context = context,
        namespace = CredentialNamespace.forProvider(providerId),
        legacyNamespace = CredentialNamespace.legacy.takeIf { providerId == DEFAULT_PROVIDER_ID },
        registryProviderId = providerId,
    )

    constructor(
        context: Context,
        keyAlias: String,
        preferencesName: String,
        legacyKeyAlias: String? = null,
        legacyPreferencesName: String? = null,
    ) : this(
        context = context,
        namespace = AcademicCredentialNamespace(preferencesName, keyAlias),
        legacyNamespace = if (legacyKeyAlias != null && legacyPreferencesName != null) {
            AcademicCredentialNamespace(legacyPreferencesName, legacyKeyAlias)
        } else {
            null
        },
        registryProviderId = null,
    )

    fun save(credentials: AcademicCredentials) {
        val username = credentials.username.trim()
        require(username.isNotEmpty()) { "用户名不能为空" }
        require(credentials.password.isNotEmpty()) { "密码不能为空" }
        val plaintext = JSONObject()
            .put("username", username)
            .put("password", credentials.password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey(namespace.keyAlias))
        }
        val encrypted = cipher.doFinal(plaintext)
        check(
            preferences.edit()
                .putInt(KEY_VERSION, FORMAT_VERSION)
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit(),
        ) { "无法保存加密账号" }
    }

    fun load(): AcademicCredentials? {
        if (preferences.contains(KEY_CIPHERTEXT)) {
            val current = runCatching { decrypt(preferences, namespace.keyAlias) }.getOrElse {
                discardStoredCiphertext(namespace)
                return null
            }
            clearLegacyNamespace()
            return current
        }
        return migrateLegacy()
    }

    fun clear() {
        clearNamespace(namespace)
        legacyNamespace?.let(::clearNamespace)
    }

    private fun migrateLegacy(): AcademicCredentials? {
        val legacy = legacyNamespace ?: return null
        val legacyPreferences = preferencesFor(legacy)
        if (!legacyPreferences.contains(KEY_CIPHERTEXT)) return null
        val credentials = runCatching { decrypt(legacyPreferences, legacy.keyAlias) }.getOrNull()
            ?: return null
        return runCatching {
            save(credentials)
            clearNamespace(legacy)
            credentials
        }.getOrNull()
    }

    private fun clearLegacyNamespace() {
        legacyNamespace?.takeIf { preferencesFor(it).contains(KEY_CIPHERTEXT) }?.let(::clearNamespace)
    }

    private fun decrypt(source: SharedPreferences, keyAlias: String): AcademicCredentials {
        require(source.getInt(KEY_VERSION, 0) == FORMAT_VERSION)
        val iv = Base64.decode(requireNotNull(source.getString(KEY_IV, null)), Base64.NO_WRAP)
        val ciphertext = Base64.decode(
            requireNotNull(source.getString(KEY_CIPHERTEXT, null)),
            Base64.NO_WRAP,
        )
        val key = androidKeyStore().getKey(keyAlias, null) as? SecretKey
            ?: throw GeneralSecurityException("缺少账号加密密钥")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val value = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
        return AcademicCredentials(
            username = value.getString("username").also { require(it.isNotBlank()) },
            password = value.getString("password").also { require(it.isNotEmpty()) },
        )
    }

    private fun discardStoredCiphertext(value: AcademicCredentialNamespace) {
        check(preferencesFor(value).edit().clear().commit()) {
            "无法清除加密账号"
        }
    }

    private fun clearNamespace(value: AcademicCredentialNamespace) {
        discardStoredCiphertext(value)
        runCatching {
            androidKeyStore().apply {
                if (containsAlias(value.keyAlias)) deleteEntry(value.keyAlias)
            }
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun getOrCreateKey(keyAlias: String): SecretKey {
        val keyStore = androidKeyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun preferencesFor(value: AcademicCredentialNamespace): SharedPreferences =
        preferences.takeIf { value == namespace }
            ?: appContext.getSharedPreferences(value.preferencesName, Context.MODE_PRIVATE)

    companion object {
        const val PREFERENCES_NAME = "courseaxis_secure_credentials"
        const val DEFAULT_KEY_ALIAS = "dev.shusenzheng64.courseaxis.academic-login"
        const val DEFAULT_PROVIDER_ID = "nfu-zhengfang"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val KEY_VERSION = "version"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"

        fun maskUsername(username: String): String {
            val value = username.trim()
            return when (value.length) {
                0 -> ""
                1 -> "•"
                2 -> "${value.first()}•"
                in 3..5 -> "${value.first()}${"•".repeat(value.length - 2)}${value.last()}"
                else -> value.take(2) + "•".repeat(value.length - 4) + value.takeLast(2)
            }
        }
    }
}
