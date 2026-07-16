package dev.shusenzheng64.courseaxis.importer

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class AcademicProvider(
    val schemaVersion: Int = 1,
    val id: String,
    val schoolName: String,
    val vendor: String,
    val allowedOrigin: String,
    val pathPrefix: String,
    val loginPath: String,
    val homePath: String,
    val requiresThirdPartyCookies: Boolean,
    val semesterCodes: Map<String, String> = emptyMap(),
    val adapterId: String = "",
    val sessionNamespace: String = "",
)

object AcademicProviderPolicy {
    fun isAllowed(url: String, provider: AcademicProvider): Boolean {
        val origin = parse(provider.allowedOrigin) ?: return false
        val candidate = parse(url) ?: return false
        if (!isValidOrigin(origin) || !isValidPrefix(provider.pathPrefix)) return false
        if (!isHttps(candidate) || candidate.userInfo != null) return false
        if (!candidate.host.equals(origin.host, ignoreCase = true)) return false
        if (effectivePort(candidate) != effectivePort(origin)) return false

        val decodedPath = decodePath(candidate.rawPath ?: return false) ?: return false
        if (decodedPath.contains('\\') || decodedPath.contains('\u0000')) return false
        if (decodedPath.split('/').any { it == "." || it == ".." }) return false
        return decodedPath.startsWith(provider.pathPrefix)
    }

    fun isAllowedOrigin(value: String, provider: AcademicProvider): Boolean {
        val origin = parse(provider.allowedOrigin) ?: return false
        val candidate = parse(value) ?: return false
        return isValidOrigin(origin) &&
            isValidOrigin(candidate) &&
            candidate.host.equals(origin.host, ignoreCase = true) &&
            effectivePort(candidate) == effectivePort(origin)
    }

    private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun isValidOrigin(origin: URI): Boolean =
        isHttps(origin) &&
            origin.host != null &&
            origin.userInfo == null &&
            origin.query == null &&
            origin.fragment == null &&
            (origin.path.isNullOrEmpty() || origin.path == "/")

    private fun isHttps(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) && uri.port in setOf(-1, 443)

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun isValidPrefix(prefix: String): Boolean =
        prefix.startsWith('/') &&
            prefix.endsWith('/') &&
            !prefix.contains('\\') &&
            prefix.split('/').none { it == "." || it == ".." }

    private fun decodePath(rawPath: String): String? = runCatching {
        URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrNull()
}
