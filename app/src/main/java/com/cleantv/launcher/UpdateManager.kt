package com.cleantv.launcher

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String
)

object UpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/chayapolpmpropertyphuket-lgtm/cleantv/releases/latest"
    private const val APK_NAME = "CleanTV.apk"
    private const val MAX_APK_SIZE = 100L * 1024L * 1024L
    const val ACTION_INSTALL_STATUS = "com.cleantv.launcher.UPDATE_INSTALL_STATUS"
    const val EXTRA_VERSION_CODE = "update_version_code"

    fun fetchLatest(context: Context): UpdateRelease? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CleanTV-Android-Updater")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub ตอบกลับ HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = JSONObject(json)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return null

            val tag = release.optString("tag_name")
            val remoteCode = Regex("^v?(\\d+)$").matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IOException("รูปแบบ Release tag ไม่ถูกต้อง: $tag")

            val current = currentVersionCode(context)
            if (remoteCode <= current) return null

            val assets = release.optJSONArray("assets") ?: throw IOException("Release ไม่มีไฟล์ APK")
            var apkAsset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") == APK_NAME) {
                    apkAsset = asset
                    break
                }
            }
            val asset = apkAsset ?: throw IOException("ไม่พบ CleanTV.apk ใน Release ล่าสุด")
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/chayapolpmpropertyphuket-lgtm/cleantv/releases/download/")) {
                throw IOException("URL ดาวน์โหลด APK ไม่ผ่านการตรวจสอบ")
            }
            val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
            if (!digest.matches(Regex("^[a-f0-9]{64}$"))) {
                throw IOException("Release ไม่มี SHA-256 ที่ถูกต้อง")
            }

            return UpdateRelease(
                versionCode = remoteCode,
                versionName = tag,
                notes = release.optString("body").trim(),
                downloadUrl = url,
                sha256 = digest
            )
        } finally {
            connection.disconnect()
        }
    }

    fun installLatest(context: Context, release: UpdateRelease) {
        val apk = downloadAndVerify(context, release)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android จะข้ามหน้าขอยืนยันได้เฉพาะเมื่อเข้าเงื่อนไข installer/update-owner
                // หากไม่เข้าเงื่อนไข ระบบจะส่ง STATUS_PENDING_USER_ACTION กลับมาแทน
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("CleanTV.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val statusIntent = android.content.Intent(context, UpdateStatusReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(EXTRA_VERSION_CODE, release.versionCode)
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0
                val callback = android.app.PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                session.commit(callback.intentSender)
            }
        } catch (error: Throwable) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) {}
            throw error
        }
    }

    private fun downloadAndVerify(context: Context, release: UpdateRelease): File {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(folder, "CleanTV-v${release.versionCode}.apk")
        if (target.exists() && sha256(target) == release.sha256) return target
        target.delete()

        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CleanTV-Android-Updater")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("ดาวน์โหลด APK ไม่สำเร็จ: HTTP ${connection.responseCode}")
            }
            val length = connection.contentLengthLong
            if (length > MAX_APK_SIZE) throw IOException("ไฟล์ APK ใหญ่เกินขนาดที่อนุญาต")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_SIZE) throw IOException("ไฟล์ APK ใหญ่เกินขนาดที่อนุญาต")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        if (target.length() <= 0L || sha256(target) != release.sha256) {
            target.delete()
            throw IOException("ตรวจสอบ SHA-256 ไม่ผ่าน ไฟล์อัปเดตถูกปฏิเสธ")
        }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentVersionCode(context: Context): Int {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else info.versionCode
    }
}
