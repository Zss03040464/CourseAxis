package dev.shusenzheng64.courseaxis.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSyncSettings(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    preferencesName: String = PREFERENCES_NAME,
) : SyncSettings {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    override fun load(): SyncConfiguration {
        removeLegacyFallbackOrMigrate()
        val token = if (preferences.contains(KEY_TOKEN_CIPHERTEXT)) {
            runCatching { decryptToken() }.getOrElse {
                discardStoredToken()
                ""
            }
        } else {
            ""
        }
        return SyncConfiguration(
            serverUrl = preferences.getString(KEY_SERVER_URL, "").orEmpty(),
            token = token,
            revision = preferences.getLong(KEY_REVISION, 0),
        )
    }

    override fun save(url: String, token: String) {
        val normalizedUrl = url.trim().trimEnd('/')
        val normalizedToken = token.trim()
        if (normalizedToken.isEmpty()) {
            check(
                preferences.edit()
                    .putString(KEY_SERVER_URL, normalizedUrl)
                    .remove(LEGACY_KEY_TOKEN)
                    .remove(KEY_TOKEN_VERSION)
                    .remove(KEY_TOKEN_IV)
                    .remove(KEY_TOKEN_CIPHERTEXT)
                    .commit(),
            ) { "无法保存同步设置" }
            return
        }
        val encrypted = encryptToken(normalizedToken)
        check(
            preferences.edit()
                .putString(KEY_SERVER_URL, normalizedUrl)
                .remove(LEGACY_KEY_TOKEN)
                .putInt(KEY_TOKEN_VERSION, FORMAT_VERSION)
                .putString(KEY_TOKEN_IV, encrypted.iv)
                .putString(KEY_TOKEN_CIPHERTEXT, encrypted.ciphertext)
                .commit(),
        ) { "无法保存加密同步令牌" }
    }

    override fun setRevision(value: Long) {
        check(preferences.edit().putLong(KEY_REVISION, value).commit()) {
            "无法保存同步版本"
        }
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) { "无法清除同步设置" }
        runCatching {
            androidKeyStore().apply {
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
        }
    }

    private fun removeLegacyFallbackOrMigrate() {
        if (!preferences.contains(LEGACY_KEY_TOKEN)) return
        if (preferences.contains(KEY_TOKEN_CIPHERTEXT)) {
            preferences.edit().remove(LEGACY_KEY_TOKEN).commit()
            return
        }
        val legacyToken = preferences.getString(LEGACY_KEY_TOKEN, "").orEmpty().trim()
        if (legacyToken.isEmpty()) {
            preferences.edit().remove(LEGACY_KEY_TOKEN).commit()
            return
        }
        runCatching {
            val encrypted = encryptToken(legacyToken)
            check(
                preferences.edit()
                    .remove(LEGACY_KEY_TOKEN)
                    .putInt(KEY_TOKEN_VERSION, FORMAT_VERSION)
                    .putString(KEY_TOKEN_IV, encrypted.iv)
                    .putString(KEY_TOKEN_CIPHERTEXT, encrypted.ciphertext)
                    .commit(),
            ) { "无法迁移同步令牌" }
        }.onFailure {
            discardStoredToken()
        }
    }

    private fun encryptToken(token: String): EncryptedToken {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        return EncryptedToken(
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(
                cipher.doFinal(token.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            ),
        )
    }

    private fun decryptToken(): String {
        require(preferences.getInt(KEY_TOKEN_VERSION, 0) == FORMAT_VERSION)
        val key = existingKey() ?: error("同步令牌密钥不存在")
        val iv = Base64.decode(
            requireNotNull(preferences.getString(KEY_TOKEN_IV, null)),
            Base64.NO_WRAP,
        )
        val ciphertext = Base64.decode(
            requireNotNull(preferences.getString(KEY_TOKEN_CIPHERTEXT, null)),
            Base64.NO_WRAP,
        )
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun discardStoredToken() {
        preferences.edit()
            .remove(LEGACY_KEY_TOKEN)
            .remove(KEY_TOKEN_VERSION)
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_CIPHERTEXT)
            .commit()
    }

    @Throws(GeneralSecurityException::class)
    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
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

    private fun existingKey(): SecretKey? =
        androidKeyStore().getKey(keyAlias, null) as? SecretKey

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private data class EncryptedToken(
        val iv: String,
        val ciphertext: String,
    )

    companion object {
        const val PREFERENCES_NAME = "sync"
        const val DEFAULT_KEY_ALIAS = "dev.shusenzheng64.courseaxis.self-hosted-sync"
        const val KEY_TOKEN_CIPHERTEXT = "tokenCiphertext"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val KEY_SERVER_URL = "serverUrl"
        private const val LEGACY_KEY_TOKEN = "token"
        private const val KEY_REVISION = "revision"
        private const val KEY_TOKEN_VERSION = "tokenVersion"
        private const val KEY_TOKEN_IV = "tokenIv"
    }
}
