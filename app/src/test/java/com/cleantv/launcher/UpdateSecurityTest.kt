package com.cleantv.launcher

import org.junit.Assert.*
import org.junit.Test

class UpdateSecurityTest {
    @Test fun acceptsNumericReleaseTags() {
        assertEquals(7, UpdateSecurity.parseVersionCode("v7"))
        assertEquals(12, UpdateSecurity.parseVersionCode("12"))
    }

    @Test fun rejectsMalformedOrOverflowReleaseTags() {
        assertNull(UpdateSecurity.parseVersionCode("latest"))
        assertNull(UpdateSecurity.parseVersionCode("v7-beta"))
        assertNull(UpdateSecurity.parseVersionCode("v"))
        assertNull(UpdateSecurity.parseVersionCode("v0"))
        assertNull(UpdateSecurity.parseVersionCode("v999999999999999999999"))
    }

    @Test fun acceptsOnlySha256Digest() {
        val digest = "a".repeat(64)
        assertEquals(digest, UpdateSecurity.normalizeSha256("sha256:$digest"))
        assertNull(UpdateSecurity.normalizeSha256("sha256:abcd"))
        assertNull(UpdateSecurity.normalizeSha256("b".repeat(63)))
    }

    @Test fun releaseUrlMustBeHttpsAndBelongToExactRepository() {
        assertTrue(UpdateSecurity.isAllowedReleaseUrl(
            "https://github.com/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/v7/CleanTV.apk"
        ))
        assertFalse(UpdateSecurity.isAllowedReleaseUrl(
            "http://github.com/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/v7/CleanTV.apk"
        ))
        assertFalse(UpdateSecurity.isAllowedReleaseUrl(
            "https://evil.example/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/v7/CleanTV.apk"
        ))
        assertFalse(UpdateSecurity.isAllowedReleaseUrl(
            "https://github.com/another/repo/releases/download/v7/CleanTV.apk"
        ))
        assertFalse(UpdateSecurity.isAllowedReleaseUrl(
            "https://github.com:8443/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/v7/CleanTV.apk"
        ))
        assertFalse(UpdateSecurity.isAllowedReleaseUrl(
            "https://github.com@evil.example/chayapolpropertyphuket-lgtm/cleantv/releases/download/v7/CleanTV.apk"
        ))
    }

    @Test fun redirectMustUseHttpsAndTrustedGitHubAssetHost() {
        assertTrue(UpdateSecurity.isAllowedRedirectUrl("https://release-assets.githubusercontent.com/file.apk"))
        assertTrue(UpdateSecurity.isAllowedRedirectUrl("https://objects.githubusercontent.com/file.apk"))
        assertFalse(UpdateSecurity.isAllowedRedirectUrl("http://release-assets.githubusercontent.com/file.apk"))
        assertFalse(UpdateSecurity.isAllowedRedirectUrl("https://evil.example/file.apk"))
        assertFalse(UpdateSecurity.isAllowedRedirectUrl("https://release-assets.githubusercontent.com:8443/file.apk"))
        assertFalse(UpdateSecurity.isAllowedRedirectUrl("https://release-assets.githubusercontent.com@evil.example/file.apk"))
    }

    @Test fun apkSizeMustBePositiveAndAtMost100MiB() {
        assertTrue(UpdateSecurity.isAllowedApkSize(1L))
        assertTrue(UpdateSecurity.isAllowedApkSize(100L * 1024L * 1024L))
        assertFalse(UpdateSecurity.isAllowedApkSize(0L))
        assertFalse(UpdateSecurity.isAllowedApkSize(100L * 1024L * 1024L + 1L))
    }
}
