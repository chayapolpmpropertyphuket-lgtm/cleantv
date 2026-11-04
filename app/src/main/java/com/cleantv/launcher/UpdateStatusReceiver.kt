package com.cleantv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                    try {
                        context.startActivity(it)
                    } catch (_: Exception) {
                        Toast.makeText(context, "กรุณากลับเข้า CleanTV เพื่อดำเนินการติดตั้งต่อ", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val version = intent.getIntExtra(UpdateManager.EXTRA_VERSION_CODE, -1)
                if (version > 0) File(context.cacheDir, "updates/CleanTV-v$version.apk").delete()
                Toast.makeText(context, "CleanTV อัปเดตเรียบร้อยแล้ว", Toast.LENGTH_LONG).show()
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(
                    context,
                    if (message.isBlank()) "ติดตั้งอัปเดตไม่สำเร็จ กรุณาลองอีกครั้ง" else "อัปเดตไม่สำเร็จ: $message",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
