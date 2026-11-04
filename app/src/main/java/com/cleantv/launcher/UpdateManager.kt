package com.cleantv.launcher

import android.content.Context
import java.io.IOException

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String
)

object UpdateManager {
    // รุ่นติดตั้งคู่ขนานยังไม่ใช้ Release feed ของแอปหลัก เพราะ APK จาก feed นั้น
    // มี applicationId คนละตัวและห้ามส่งเข้า PackageInstaller ของรุ่นนี้
    const val ACTION_INSTALL_STATUS = "com.cleantv.launcher.UPDATE_INSTALL_STATUS"
    const val EXTRA_VERSION_CODE = "update_version_code"

    fun fetchLatest(context: Context): UpdateRelease? {
        throw IOException("ระบบอัปเดตของรุ่นทดสอบยังไม่เปิดใช้งาน")
    }

    fun installLatest(context: Context, release: UpdateRelease) {
        throw IOException("ปิดการติดตั้งอัปเดตระยะไกลไว้ในรุ่นทดสอบ")
    }
}
