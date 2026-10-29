package com.cleantv.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executors

abstract class BaseActivity : Activity() {

    protected val prefs: Prefs by lazy { Prefs(this) }
    protected val worker = Executors.newSingleThreadExecutor()
    protected val main = Handler(Looper.getMainLooper())

    private var pendingKey: String? = null
    private var pendingDone: (() -> Unit)? = null

    // ---------- เครื่องมือทั่วไป ----------
    protected fun dialog(): AlertDialog.Builder =
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    protected fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    protected fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("ไม่พบหน้าจอที่ต้องการบนทีวีเครื่องนี้")
        }
    }

    protected fun confirm(message: String, onYes: () -> Unit) {
        dialog().setMessage(message)
            .setPositiveButton("ยืนยัน") { _, _ -> onYes() }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    protected fun askText(title: String, initial: String, hint: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            this.hint = hint
            setSingleLine()
            setSelection(text.length)
        }
        dialog().setTitle(title)
            .setView(wrap(input))
            .setPositiveButton("ตกลง") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    protected fun choose(title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        dialog().setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { d, which ->
                d.dismiss()
                onPick(which)
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun wrap(v: View): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        return FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(v)
        }
    }

    // ---------- ระบบเลือกรูปภาพ ----------
    protected fun pickImage(title: String, key: String, onDone: () -> Unit) {
        val options = listOf(
            "ดาวน์โหลดจากลิงก์ (URL)",
            "เลือกจากไฟล์ในทีวี / แฟลชไดรฟ์ USB",
            "ใช้ตัวเลือกไฟล์ของระบบ"
        )
        dialog().setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> askUrl(key, onDone)
                    1 -> browseFiles(key, onDone)
                    else -> systemPicker(key, onDone)
                }
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun askUrl(key: String, onDone: () -> Unit) {
        askText("ลิงก์รูปภาพ (JPG / PNG / WEBP)", "https://", "https://...") { url ->
            if (url.length < 12) {
                toast("ลิงก์ไม่ถูกต้อง")
                return@askText
            }
            toast("กำลังดาวน์โหลด...")
            runImport(onDone) { ImageStore.saveFromUrl(this, key, url) }
        }
    }

    private fun runImport(onDone: () -> Unit, job: () -> Unit) {
        worker.execute {
            val error = try {
                job()
                null
            } catch (e: Throwable) {
                e.message ?: e.javaClass.simpleName
            }
            main.post {
                if (error == null) {
                    prefs.touch()
                    toast("บันทึกรูปเรียบร้อย")
                    onDone()
                } else {
                    toast("ไม่สำเร็จ: $error")
                }
            }
        }
    }

    private fun browseFiles(key: String, onDone: () -> Unit) {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            pendingKey = key
            pendingDone = onDone
            requestPermissions(arrayOf(perm), REQ_PERM)
            return
        }
        showDir(null, key, onDone)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        val k = pendingKey
        val d = pendingDone
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            k != null && d != null
        ) {
            showDir(null, k, d)
        } else {
            toast("ต้องอนุญาตสิทธิ์อ่านไฟล์ก่อน จึงจะเลือกรูปจากเครื่องได้")
        }
    }

    private fun roots(): List<File> {
        val out = ArrayList<File>()
        @Suppress("DEPRECATION")
        val ext = Environment.getExternalStorageDirectory()
        for (name in listOf("Download", "Pictures", "DCIM")) {
            val f = File(ext, name)
            if (f.isDirectory) out.add(f)
        }
        out.add(ext)
        // แฟลชไดรฟ์ USB จะอยู่ใน /storage/<รหัส>
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.canRead() && f.name != "emulated" && f.name != "self") out.add(f)
        }
        return out
    }

    private fun isImage(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun showDir(dir: File?, key: String, onDone: () -> Unit) {
        val roots = roots()
        val entries: List<File> = if (dir == null) roots else
            (dir.listFiles()
                ?.filter { !it.name.startsWith(".") && (it.isDirectory || isImage(it)) }
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList())

        val labels = ArrayList<String>()
        if (dir != null) labels.add("⬅  ย้อนกลับ")
        for (f in entries) {
            labels.add(
                when {
                    dir == null -> "📂  ${f.path}"
                    f.isDirectory -> "📁  ${f.name}"
                    else -> "🖼  ${f.name}"
                }
            )
        }
        if (entries.isEmpty()) labels.add("(ไม่มีรูปภาพในโฟลเดอร์นี้)")
        val offset = if (dir != null) 1 else 0

        dialog().setTitle(dir?.path ?: "เลือกตำแหน่งที่เก็บรูป")
            .setItems(labels.toTypedArray()) { _, which ->
                if (dir != null && which == 0) {
                    showDir(if (roots.contains(dir)) null else dir.parentFile, key, onDone)
                    return@setItems
                }
                val f = entries.getOrNull(which - offset) ?: return@setItems
                if (dir == null || f.isDirectory) {
                    showDir(f, key, onDone)
                } else {
                    runImport(onDone) { FileInputStream(f).use { ImageStore.save(this, key, it) } }
                }
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun systemPicker(key: String, onDone: () -> Unit) {
        pendingKey = key
        pendingDone = onDone
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: ActivityNotFoundException) {
            toast("ทีวีนี้ไม่มีตัวเลือกไฟล์ของระบบ ให้ใช้ 'เลือกจากไฟล์ในทีวี' แทน")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val k = pendingKey ?: return
        val d = pendingDone ?: return
        runImport(d) {
            val input = contentResolver.openInputStream(uri) ?: throw IOException("เปิดไฟล์ไม่ได้")
            ImageStore.save(this, k, input)
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERM = 41
        private const val REQ_PICK = 42
    }
}