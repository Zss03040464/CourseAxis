package dev.shusenzheng64.courseaxis.importer

import android.content.res.AssetManager
import org.json.JSONObject
import java.util.UUID

object AcademicProviderCatalog {
    fun load(assets: AssetManager): List<AcademicProvider> {
        val catalogJson = assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        val ids = validatedCatalogIds(catalogJson)
        val providerJsonById = ids.associateWith { id ->
            assets.open("$id/provider.json").bufferedReader().use { it.readText() }
        }
        return decode(catalogJson, providerJsonById)
    }

    fun decode(
        catalogJson: String,
        providerJsonById: Map<String, String>,
    ): List<AcademicProvider> {
        val ids = validatedCatalogIds(catalogJson)
        require(providerJsonById.keys == ids.toSet()) { "Provider Catalog 与配置文件不一致" }

        val providers = ids.map { expectedId ->
            val value = JSONObject(requireNotNull(providerJsonById[expectedId]))
            val semesterCodes = value.getJSONObject("semesterCodes").let { semesters ->
                semesters.keys().asSequence().associateWith { key -> semesters.getString(key) }
            }
            val provider = AcademicProvider(
                schemaVersion = value.getInt("schemaVersion"),
                id = value.getString("id"),
                schoolName = value.getString("schoolName"),
                vendor = value.getString("vendor"),
                allowedOrigin = value.getString("allowedOrigin"),
                pathPrefix = value.getString("pathPrefix"),
                loginPath = value.getString("loginPath"),
                homePath = value.getString("homePath"),
                requiresThirdPartyCookies = value.getBoolean("requiresThirdPartyCookies"),
                semesterCodes = semesterCodes,
                adapterId = value.getString("adapterId"),
                sessionNamespace = value.getString("sessionNamespace"),
            )
            validate(expectedId, provider)
            provider
        }
        require(providers.map { it.sessionNamespace }.distinct().size == providers.size) {
            "Provider 会话命名空间不能重复"
        }
        return providers
    }

    private fun validate(expectedId: String, provider: AcademicProvider) {
        require(provider.schemaVersion == SCHEMA_VERSION) { "不支持的 Provider 版本：${provider.id}" }
        require(provider.id == expectedId) { "Provider ID 与目录不一致：$expectedId" }
        requireSafeId(provider.id)
        require(provider.schoolName.isNotBlank()) { "Provider 学校名称不能为空：${provider.id}" }
        require(provider.vendor.isNotBlank()) { "Provider 厂商不能为空：${provider.id}" }
        require(provider.semesterCodes.isNotEmpty()) { "Provider 学期代码不能为空：${provider.id}" }
        require(provider.semesterCodes.keys.all { it in SEMESTER_KEYS }) {
            "Provider 包含不支持的学期语义：${provider.id}"
        }
        require(provider.semesterCodes.values.all { it.isNotBlank() }) {
            "Provider 学期代码不能为空：${provider.id}"
        }
        require(provider.semesterCodes.values.distinct().size == provider.semesterCodes.size) {
            "Provider 学期代码不能重复：${provider.id}"
        }
        require(
            runCatching { UUID.fromString(provider.sessionNamespace).toString() }
                .getOrNull() == provider.sessionNamespace.lowercase(),
        ) { "Provider 会话命名空间不是规范 UUID：${provider.id}" }
        require(AcademicProviderPolicy.isAllowed(provider.allowedOrigin + provider.pathPrefix, provider)) {
            "Provider origin 或路径无效：${provider.id}"
        }
        require(AcademicProviderPolicy.isAllowed(provider.allowedOrigin + provider.loginPath, provider)) {
            "Provider 登录路径越界：${provider.id}"
        }
        require(AcademicProviderPolicy.isAllowed(provider.allowedOrigin + provider.homePath, provider)) {
            "Provider 首页路径越界：${provider.id}"
        }
        AcademicImportAdapterRegistry.adapter(provider)
    }

    private fun requireSafeId(id: String) {
        require(SAFE_ID.matches(id)) { "Provider ID 不安全：$id" }
    }

    private fun validatedCatalogIds(catalogJson: String): List<String> {
        val catalog = JSONObject(catalogJson)
        require(catalog.getInt("schemaVersion") == SCHEMA_VERSION) {
            "不支持的 Provider Catalog 版本"
        }
        val values = catalog.getJSONArray("providers")
        val ids = buildList {
            for (index in 0 until values.length()) add(values.getString(index))
        }
        require(ids.isNotEmpty()) { "Provider Catalog 不能为空" }
        require(ids.distinct().size == ids.size) { "Provider Catalog 包含重复学校" }
        ids.forEach(::requireSafeId)
        return ids
    }

    private const val CATALOG_ASSET = "catalog.json"
    private const val SCHEMA_VERSION = 1
    private val SAFE_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val SEMESTER_KEYS = setOf("first", "second", "third")
}
