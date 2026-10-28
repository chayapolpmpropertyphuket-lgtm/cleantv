package com.cleantv.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        const val SPAN = 5

        // แอปที่จะอยู่หน้าสุด (เรียงตามลำดับในลิสต์นี้)
        val PINNED = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tvmusic"
        )
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var grid: RecyclerView
    private lateinit var adapter: AppAdapter
    private var firstLoad = true

    // อัปเดตรายการแอปเองเมื่อมีการติดตั้งหรือลบแอป
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = AppAdapter(SPAN, ::launchApp, ::openAppInfo)
        grid = findViewById(R.id.appGrid)
        grid.layoutManager = GridLayoutManager(this, SPAN)
        grid.setHasFixedSize(true)
        grid.itemAnimator = null // ปิดแอนิเมชันรายการ ลดภาระ GPU
        grid.adapter = adapter

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            safeStart(Intent(Settings.ACTION_SETTINGS))
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.greeting).text = greeting()
    }

    // กดปุ่ม Home ขณะอยู่หน้านี้: เลื่อนกลับไปแอปแรก
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        grid.scrollToPosition(0)
        grid.post { grid.getChildAt(0)?.requestFocus() }
    }

    // โหลดรายการแอปในเธรดเบื้องหลัง เพื่อไม่ให้หน้าจอค้าง
    private fun loadApps() {
        worker.execute {
            val pm = packageManager
            val seen = HashSet<String>()
            val list = ArrayList<AppEntry>()

            for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val query = Intent(Intent.ACTION_MAIN).addCategory(category)
                for (ri in pm.queryIntentActivities(query, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || !seen.add(pkg)) continue
                    list.add(
                        AppEntry(
                            label = ri.loadLabel(pm).toString(),
                            packageName = pkg,
                            banner = ri.activityInfo.loadBanner(pm),
                            icon = ri.loadIcon(pm)
                        )
                    )
                }
            }

            list.sortWith(
                compareBy<AppEntry>(
                    { PINNED.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.label.lowercase() }
                )
            )

            mainHandler.post {
                adapter.submit(list)
                if (firstLoad) {
                    firstLoad = false
                    grid.post { grid.getChildAt(0)?.requestFocus() }
                }
            }
        }
    }

    private fun launchApp(app: AppEntry) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            toast("ไม่สามารถเปิดแอปนี้ได้")
            return
        }
        safeStart(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // กดค้างที่การ์ด: เปิดหน้าข้อมูลแอป
    private fun openAppInfo(app: AppEntry) {
        safeStart(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}")
            )
        )
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("ไม่พบหน้าจอที่ต้องการ")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun greeting() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "สวัสดีตอนเช้า"
        in 12..16 -> "สวัสดีตอนบ่าย"
        in 17..20 -> "สวัสดีตอนเย็น"
        else -> "ราตรีสวัสดิ์"
    }

    // Launcher ไม่ควรถูกปิดด้วยปุ่ม Back
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        worker.shutdown()
        super.onDestroy()
    }
}