package com.cleantv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Collator
import java.util.Calendar
import java.util.Locale

class MainActivity : BaseActivity() {

    class Act(val label: String, val run: () -> Unit)

    private lateinit var grid: RecyclerView
    private lateinit var wallpaper: ImageView
    private lateinit var dim: View
    private lateinit var logoImage: ImageView
    private lateinit var logoText: TextView
    private lateinit var greeting: TextView
    private lateinit var date: TextClock
    private lateinit var clock: TextClock

    private var adapter: HomeAdapter? = null
    private var apps: List<AppEntry> = emptyList()
    private var dirty = false
    private var focusFirst = true
    private var pendingFocus: String? = null
    private val collator: Collator = Collator.getInstance(Locale("th", "TH"))

    // มีการเปลี่ยนค่าในหน้าตั้งค่า -> โหลดหน้าหลักใหม่ตอนกลับมา
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != Prefs.KEY_COUNTS) dirty = true
    }

    // มีการติดตั้งหรือลบแอป -> โหลดรายการใหม่
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            focusFirst = true
            reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        wallpaper = findViewById(R.id.wallpaper)
        dim = findViewById(R.id.dim)
        logoImage = findViewById(R.id.logoImage)
        logoText = findViewById(R.id.logoText)
        greeting = findViewById(R.id.greeting)
        date = findViewById(R.id.date)
        clock = findViewById(R.id.clock)

        grid.setHasFixedSize(true)
        grid.itemAnimator = null

        findViewById<View>(R.id.btnCustomize).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnSystem).setOnClickListener {
            safeStart(Intent(Settings.ACTION_SETTINGS))
        }

        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        applyAll()
    }

    override fun onResume() {
        super.onResume()
        if (dirty) {
            dirty = false
            applyAll()
        }
        updateGreeting()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val pos = adapter?.firstAppPosition() ?: -1
        if (pos >= 0) focusAt(pos)
    }

    // ---------- นำการตั้งค่าทั้งหมดมาใช้ ----------
    private fun applyAll() {
        applyWallpaper()
        applyHeader()

        val d = resources.displayMetrics.density
        val cfg = CardConfig(
            columns = prefs.columns.coerceIn(3, 8),
            style = prefs.cardStyle,
            showLabels = prefs.showLabels,
            cornerPx = prefs.cornerDp * d,
            focusScale = prefs.focusScale / 100f,
            accent = Themes.accent(prefs.accentIndex),
            animMs = prefs.animMs.toLong(),
            strokePx = (3 * d).toInt()
        )
        val a = HomeAdapter(cfg, ::launchApp, ::showAppMenu)
        val lm = GridLayoutManager(this, cfg.columns)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (a.isHeader(position)) cfg.columns else 1
        }
        grid.layoutManager = lm
        grid.adapter = a
        adapter = a
        focusFirst = true
        reloadApps()
    }

    private fun applyWallpaper() {
        val preset = Themes.gradient(prefs.presetIndex)
        if (prefs.wallpaperMode == 1 && ImageStore.exists(this, ImageStore.WALLPAPER)) {
            dim.alpha = prefs.dimPercent.coerceIn(0, 90) / 100f
            worker.execute {
                val bmp = ImageStore.load(this, ImageStore.WALLPAPER, 1920, 1080)
                main.post {
                    if (bmp != null) wallpaper.setImageBitmap(bmp) else wallpaper.setImageDrawable(preset)
                }
            }
        } else {
            dim.alpha = 0f
            wallpaper.setImageDrawable(preset)
        }
    }

    private fun applyHeader() {
        when (prefs.logoMode) {
            2 -> {
                logoText.visibility = View.GONE
                worker.execute {
                    val bmp = ImageStore.load(this, ImageStore.LOGO, 800, 200)
                    main.post {
                        if (bmp != null) {
                            logoImage.setImageBitmap(bmp)
                            logoImage.visibility = View.VISIBLE
                        } else {
                            logoImage.visibility = View.GONE
                            logoText.text = prefs.logoText
                            logoText.visibility = View.VISIBLE
                        }
                    }
                }
            }
            1 -> {
                logoImage.visibility = View.GONE
                logoText.text = prefs.logoText
                logoText.visibility = if (prefs.logoText.isBlank()) View.GONE else View.VISIBLE
            }
            else -> {
                logoImage.visibility = View.GONE
                logoText.visibility = View.GONE
            }
        }
        greeting.visibility = if (prefs.showGreeting) View.VISIBLE else View.GONE
        date.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
        clock.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        val fmt = if (prefs.use24h) "HH:mm" else "h:mm a"
        clock.format24Hour = fmt
        clock.format12Hour = fmt
        updateGreeting()
    }

    private fun updateGreeting() {
        greeting.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "สวัสดีตอนเช้า"
            in 12..16 -> "สวัสดีตอนบ่าย"
            in 17..20 -> "สวัสดีตอนเย็น"
            else -> "ราตรีสวัสดิ์"
        }
    }

    // ---------- รายการแอป ----------
    private fun reloadApps() {
        worker.execute {
            val loaded = AppLoader.load(this, prefs)
            main.post {
                apps = loaded
                render()
            }
        }
    }

    private fun render() {
        val a = adapter ?: return
        a.submit(buildItems())
        val target = pendingFocus
        pendingFocus = null
        val pos = when {
            target != null -> a.positionOf(target)
            focusFirst -> a.firstAppPosition()
            else -> -1
        }
        focusFirst = false
        if (pos >= 0) focusAt(pos)
    }

    private fun focusAt(pos: Int, tries: Int = 6) {
        grid.scrollToPosition(pos)
        grid.postDelayed({
            val v = grid.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else if (tries > 0) focusAt(pos, tries - 1)
        }, 50)
    }

    private fun buildItems(): List<HomeItem> {
        val hidden = prefs.hidden
        val visible = apps.filter { it.packageName !in hidden }
        val byPkg = visible.associateBy { it.packageName }
        val favPkgs = prefs.favorites
        val favApps = favPkgs.mapNotNull { byPkg[it] }
        val out = ArrayList<HomeItem>()

        val showFav = prefs.showFavorites && favApps.isNotEmpty()
        if (showFav) {
            out.add(HomeItem.Header("รายการโปรด"))
            favApps.forEach { out.add(HomeItem.App(it)) }
        }

        if (prefs.showAllApps || out.isEmpty()) {
            val favSet = favPkgs.toSet()
            val rest = if (showFav) visible.filter { it.packageName !in favSet } else visible
            val sorted = if (prefs.sortMode == 1) {
                val counts = prefs.launchCounts()
                rest.sortedWith(
                    compareByDescending<AppEntry> { counts[it.packageName] ?: 0 }
                        .thenBy(collator) { it.label }
                )
            } else {
                rest.sortedWith(compareBy(collator) { it.label })
            }
            if (sorted.isNotEmpty()) {
                out.add(HomeItem.Header(if (showFav) "แอปทั้งหมด" else "แอปของคุณ"))
                sorted.forEach { out.add(HomeItem.App(it)) }
            }
        }
        return out
    }

    private fun launchApp(app: AppEntry) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            toast("ไม่สามารถเปิดแอปนี้ได้")
            return
        }
        prefs.countLaunch(app.packageName)
        safeStart(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- เมนูกดค้างที่แอป ----------
    private fun update(focusPkg: String?, block: () -> Unit) {
        block()
        dirty = false
        pendingFocus = focusPkg
        reloadApps()
    }

    private fun moved(list: List<String>, from: Int, to: Int): List<String> {
        val m = list.toMutableList()
        val x = m.removeAt(from)
        m.add(to, x)
        return m
    }

    private fun showAppMenu(app: AppEntry) {
        val pkg = app.packageName
        val hidden = prefs.hidden
        val installed = apps.map { it.packageName }.toSet()
        val favs = prefs.favorites.filter { it in installed && it !in hidden }
        val idx = favs.indexOf(pkg)
        val acts = ArrayList<Act>()

        acts.add(Act("เปิดแอป") { launchApp(app) })
        if (idx >= 0) {
            acts.add(Act("นำออกจากรายการโปรด") { update(pkg) { prefs.favorites = favs - pkg } })
            if (idx > 0) {
                acts.add(Act("เลื่อนไปทางซ้าย") { update(pkg) { prefs.favorites = moved(favs, idx, idx - 1) } })
            }
            if (idx < favs.size - 1) {
                acts.add(Act("เลื่อนไปทางขวา") { update(pkg) { prefs.favorites = moved(favs, idx, idx + 1) } })
            }
        } else {
            acts.add(Act("เพิ่มในรายการโปรด") { update(pkg) { prefs.favorites = favs + pkg } })
        }
        acts.add(Act("เปลี่ยนชื่อ") {
            askText("เปลี่ยนชื่อ (ชื่อเดิม: ${app.originalLabel})", app.label, "เว้นว่างเพื่อใช้ชื่อเดิม") { text ->
                update(pkg) {
                    prefs.setCustomName(pkg, if (text == app.originalLabel) null else text)
                }
            }
        })
        acts.add(Act("เปลี่ยนไอคอน / แบนเนอร์") {
            pickImage("รูปสำหรับ ${app.label} (แนะนำ 16:9 เช่น 640×360)", ImageStore.iconKey(pkg)) {
                update(pkg) { }
            }
        })
        if (app.customImage != null) {
            acts.add(Act("คืนค่าไอคอนเดิม") {
                update(pkg) { ImageStore.delete(this, ImageStore.iconKey(pkg)) }
            })
        }
        acts.add(Act("ซ่อนแอปนี้") { update(null) { prefs.hidden = hidden + pkg } })
        acts.add(Act("ข้อมูลแอป (ล้างแคช / บังคับหยุด)") {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        })
        acts.add(Act("ถอนการติดตั้ง") {
            safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        })

        dialog().setTitle(app.label)
            .setItems(acts.map { it.label }.toTypedArray()) { _, which -> acts[which].run() }
            .show()
    }

    // Launcher ไม่ควรถูกปิดด้วยปุ่ม Back
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }
}