package com.cleantv.launcher

import java.net.URI

internal object UpdateSecurity {
    private const val MAX_APK_SIZE = 100L * 1024L * 1024L
    private val sha256Pattern = Regex("^[a-f0-9]{64}$")

    fun parseVersionCode(tag: String): Int? =
        Regex("^v?(\\d+)$").matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    fun normalizeSha256(value: String): String? {
        val digest = value.removePrefix("sha256:").lowercase()
        return digest.takeIf { sha256Pattern.matches(it) }
    }

    fun isAllowedReleaseUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null &&
            uri.path.orEmpty().startsWith("/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/")
    } catch (_: Exception) {
        false
    }

    fun isAllowedRedirectUrl(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        val trustedHost = host == "github.com" ||
            host == "release-assets.githubusercontent.com" ||
            host == "objects.githubusercontent.com"
        uri.scheme.equals("https", ignoreCase = true) &&
            trustedHost &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null
    } catch (_: Exception) {
        false
    }

    fun isAllowedApkSize(size: Long): Boolean = size in 1..MAX_APK_SIZE
    fun exceedsMaxApkSize(size: Long): Boolean = size > MAX_APK_SIZE
}
