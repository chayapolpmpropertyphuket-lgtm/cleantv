package com.cleantv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

// รูปทุกรูปจะถูกคัดลอกมาเก็บในแอป ไฟล์ต้นฉบับจะลบทิ้งได้โดยไม่มีผล
object ImageStore {

    const val WALLPAPER = "wallpaper"
    const val LOGO = "logo"
    fun iconKey(pkg: String) = "icon_$pkg"

    fun file(ctx: Context, key: String) = File(ctx.filesDir, "img_$key")
    fun exists(ctx: Context, key: String) = file(ctx, key).exists()

    fun delete(ctx: Context, key: String) {
        file(ctx, key).delete()
    }

    fun deleteAllIcons(ctx: Context) {
        ctx.filesDir.listFiles()
            ?.filter { it.name.startsWith("img_icon_") }
            ?.forEach { it.delete() }
    }

    fun save(ctx: Context, key: String, input: InputStream) {
        val tmp = File(ctx.filesDir, "img_$key.tmp")
        input.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }

        // ตรวจว่าเป็นไฟล์รูปที่ Android อ่านได้จริง
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            tmp.delete()
            throw IOException("ไฟล์นี้ไม่ใช่รูปภาพที่รองรับ (ใช้ JPG, PNG หรือ WEBP)")
        }

        val dest = file(ctx, key)
        dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    fun saveFromUrl(ctx: Context, key: String, rawUrl: String) {
        val conn = URL(normalizeUrl(rawUrl)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 CleanTV")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("เซิร์ฟเวอร์ตอบกลับรหัส $code")
            save(ctx, key, conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    // รองรับลิงก์หน้าไฟล์ของ GitHub (…/blob/…) โดยแปลงเป็นลิงก์ไฟล์ดิบให้อัตโนมัติ
    fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        if (u.contains("://github.com/") && u.contains("/blob/")) {
            u = u.replace("/blob/", "/raw/")
        }
        return u
    }

    // โหลดรูปแบบย่อขนาด เพื่อประหยัดแรม
    fun load(ctx: Context, key: String, maxW: Int, maxH: Int): Bitmap? {
        val f = file(ctx, key)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
            sample *= 2
        }
        return try {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}