package com.cleantv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    class Act(val label: String, val run: () -> Unit)

    companion object {
        private const val ROW_FAV = 0
        private const val ROW_APPS = 1
        private const val ROW_ALL = 2
    }

    private lateinit var root: View
    private lateinit var heroArt: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var logoText: TextView
    private lateinit var clock: TextClock
    private lateinit var tabHome: TextView
    private lateinit var tabAll: TextView
    private lateinit var pageHome: View
    private lateinit var heroBox: View
    private lateinit var heroTag: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var favTitle: View
    private lateinit var appsTitle: View
    private lateinit var rowFav: RecyclerView
    private lateinit var rowApps: RecyclerView
    private lateinit var gridAll: RecyclerView

    private var favAdapter: TvAdapter? = null
    private var appsAdapter: TvAdapter? = null
    private var allAdapter: TvAdapter? = null

    private var apps: List<AppEntry> = emptyList()
    private var favList: List<AppEntry> = emptyList()
    private var appList: List<AppEntry> = emptyList()

    private var currentTab = 0
    private var allColumns = 5
    private var lastFav = 0
    private var lastApp = 0
    private var lastAll = 0
    private var dirty = false
    private var focusFirst = true
    private var pendingFocus: String? = null
    private var heroPkg: String? = null
    private var pendingHero: AppEntry? = null
    private var artAlpha = 0.85f
    private var wallpaperBmp: Bitmap? = null

    private val hiRes = LruCache<String, Bitmap>(4)
    private val collator: Collator = Collator.getInstance(Locale("th", "TH"))
    private val heroRunnable = Runnable { pendingHero?.let { showHero(it) } }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != Prefs.KEY_COUNTS) dirty = true
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        heroArt = findViewById(R.id.heroArt)
        logoImage = findViewById(R.id.logoImage)
        logoText = findViewById(R.id.logoText)
        clock = findViewById(R.id.clock)
        tabHome = findViewById(R.id.tabHome)
        tabAll = findViewById(R.id.tabAll)
        pageHome = findViewById(R.id.pageHome)
        heroBox = findViewById(R.id.heroBox)
        heroTag = findViewById(R.id.heroTag)
        heroTitle = findViewById(R.id.heroTitle)
        heroSub = findViewById(R.id.heroSub)
        favTitle = findViewById(R.id.favTitle)
        appsTitle = findViewById(R.id.appsTitle)
        rowFav = findViewById(R.id.rowFav)
        rowApps = findViewById(R.id.rowApps)
        gridAll = findViewById(R.id.gridAll)

        listOf(rowFav, rowApps, gridAll).forEach { it.itemAnimator = null }

        // แท็บ: เลื่อนไปถึงแท็บไหน ก็สลับไปหน้านั้นทันทีแบบ Google TV
        tabHome.isSelected = true
        tabHome.setOnFocusChangeListener { _, has -> if (has) selectTab(0) }
        tabAll.setOnFocusChangeListener { _, has -> if (has) selectTab(1) }
        tabHome.setOnClickListener { focusPage() }
        tabAll.setOnClickListener { focusPage() }

        val btnCustomize = findViewById<View>(R.id.btnCustomize)
        val btnSystem = findViewById<View>(R.id.btnSystem)
        btnCustomize.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnSystem.setOnClickListener { safeStart(Intent(Settings.ACTION_SETTINGS)) }

        // กดลงจากแถบบน: กลับเข้าไปที่เนื้อหา
        val downToPage = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusPage()
                true
            } else {
                false
            }
        }
        listOf<View>(tabHome, tabAll, btnCustomize, btnSystem).forEach { it.setOnKeyListener(downToPage) }

        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        applyAll()
    }

    override fun onResume() {
        super.onResume()
        if (dirty) {
            dirty = false
            applyAll()
        } else if (heroPkg == null) {
            showDefaultHero()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectTab(0)
        lastFav = 0
        lastApp = 0
        rowFav.scrollToPosition(0)
        rowApps.scrollToPosition(0)
        focusPage()
    }

    // ---------- นำการตั้งค่ามาใช้ ----------
    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun applyAll() {
        hiRes.evictAll()
        val d = resources.displayMetrics.density
        val base = Themes.PRESETS[prefs.presetIndex.coerceIn(0, Themes.PRESETS.size - 1)].colors[0]

        root.setBackgroundColor(base)
        findViewById<View>(R.id.fadeLeft).background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(base, alpha(base, 0xB0), alpha(base, 0x30), alpha(base, 0))
        )
        findViewById<View>(R.id.fadeBottom).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(alpha(base, 0), alpha(base, 0), alpha(base, 0x99), base)
        )
        findViewById<View>(R.id.fadeTop).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(alpha(base, 0xAA), alpha(base, 0))
        )

        artAlpha = (100 - prefs.dimPercent.coerceIn(0, 80)) / 100f
        heroTag.setTextColor(Themes.accent(prefs.accentIndex))

        applyHeader()
        buildAdapters(d)
        showDefaultHero()
        loadWallpaper()
        focusFirst = true
        reloadApps()
    }

    private fun applyHeader() {
        when (prefs.logoMode) {
            2 -> {
                logoText.visibility = View.GONE
                worker.execute {
                    val bmp = ImageStore.load(this, ImageStore.LOGO, 600, 120)
                    main.post {
                        if (bmp != null) {
                            logoImage.setImageBitmap(bmp)
                            logoImage.visibility = View.VISIBLE
                        } else {
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
        clock.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        val fmt = if (prefs.use24h) "HH:mm" else "h:mm a"
        clock.format24Hour = fmt
        clock.format12Hour = fmt
    }

    private fun buildAdapters(d: Float) {
        val corner = prefs.cornerDp * d
        val scale = prefs.focusScale / 100f
        val accent = Themes.accent(prefs.accentIndex)
        val stroke = (3 * d).toInt()
        val anim = prefs.animMs.toLong()
        val margin = (10 * d).toInt()
        allColumns = prefs.columns.coerceIn(3, 8)

        fun cfg(circle: Boolean, iconOnly: Boolean, w: Int, h: Int, cols: Int) = TvConfig(
            circle = circle,
            iconOnly = iconOnly,
            widthPx = w,
            heightPx = h,
            columns = cols,
            marginPx = margin,
            showLabels = prefs.showLabels,
            cornerPx = corner,
            focusScale = scale,
            accent = accent,
            strokePx = stroke,
            animMs = anim
        )

        val fav = TvAdapter(
            ROW_FAV, cfg(false, false, (224 * d).toInt(), (126 * d).toInt(), 0),
            ::launchApp, ::showAppMenu, ::onCardFocus, ::onCardKey
        )
        val row = TvAdapter(
            ROW_APPS, cfg(true, false, (68 * d).toInt(), (68 * d).toInt(), 0),
            ::launchApp, ::showAppMenu, ::onCardFocus, ::onCardKey
        )
        val all = TvAdapter(
            ROW_ALL, cfg(false, prefs.cardStyle == 1, 0, 0, allColumns),
            ::launchApp, ::showAppMenu, ::onCardFocus, ::onCardKey
        )

        rowFav.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rowApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        gridAll.layoutManager = GridLayoutManager(this, allColumns)
        rowFav.adapter = fav
        rowApps.adapter = row
        gridAll.adapter = all
        favAdapter = fav
        appsAdapter = row
        allAdapter = all
    }

    private fun loadWallpaper() {
        wallpaperBmp = null
        if (prefs.wallpaperMode != 1 || !ImageStore.exists(this, ImageStore.WALLPAPER)) return
        worker.execute {
            val bmp = ImageStore.load(this, ImageStore.WALLPAPER, 1280, 720)
            main.post {
                wallpaperBmp = bmp
                if (heroPkg == null || prefs.heroSource == 1) setArt(wallpaperDrawable(), fade = false)
            }
        }
    }

    private fun wallpaperDrawable(): Drawable? =
        wallpaperBmp?.let { BitmapDrawable(resources, it) }

    // ---------- ส่วน Hero ----------
    private fun greeting() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "สวัสดีตอนเช้า"
        in 12..16 -> "สวัสดีตอนบ่าย"
        in 17..20 -> "สวัสดีตอนเย็น"
        else -> "ราตรีสวัสดิ์"
    }

    private fun showDefaultHero() {
        heroPkg = null
        heroTag.text = if (prefs.showDate) {
            SimpleDateFormat("EEEE d MMMM", Locale("th", "TH")).format(Date())
        } else {
            ""
        }
        heroTitle.text = if (prefs.showGreeting) greeting() else ""
        heroSub.text = "เลือกแอปด้วยรีโมท  •  กดค้างที่แอปเพื่อจัดการ"
        setArt(wallpaperDrawable(), fade = false)
    }

    private fun onCardFocus(row: Int, pos: Int, app: AppEntry) {
        when (row) {
            ROW_FAV -> lastFav = pos
            ROW_APPS -> lastApp = pos
            else -> lastAll = pos
        }
        if (row == ROW_ALL) return
        // หน่วงเล็กน้อย เวลาเลื่อนเร็ว ๆ จะได้ไม่เปลี่ยนภาพทุกช่อง
        pendingHero = app
        main.removeCallbacks(heroRunnable)
        main.postDelayed(heroRunnable, 120)
    }

    private fun showHero(app: AppEntry) {
        val pkg = app.packageName
        if (heroPkg == pkg) return
        heroPkg = pkg

        val isFav = favList.any { it.packageName == pkg }
        heroTag.text = if (isFav) "รายการโปรด" else "แอป"
        heroTitle.text = app.label
        val n = prefs.launchCounts()[pkg] ?: 0
        heroSub.text = if (n > 0) "เปิดไปแล้ว $n ครั้ง  •  กดค้างเพื่อจัดการ"
        else "กด OK เพื่อเปิด  •  กดค้างเพื่อจัดการ"
        heroBox.animate().cancel()
        heroBox.alpha = 0.35f
        heroBox.animate().alpha(1f).setDuration(200).start()

        if (prefs.heroSource == 1) return  // ใช้รูปพื้นหลังของฉันตลอด

        val key = ImageStore.iconKey(pkg)
        val cached = hiRes.get(pkg)
        val banner = app.banner
        when {
            cached != null -> setArt(BitmapDrawable(resources, cached))
            ImageStore.exists(this, key) -> {
                app.customImage?.let { setArt(it.fresh()) }
                // โหลดรูปความละเอียดสูงตามมา
                worker.execute {
                    val bmp = ImageStore.load(this, key, 1280, 720)
                    main.post {
                        if (bmp != null) {
                            hiRes.put(pkg, bmp)
                            if (heroPkg == pkg) setArt(BitmapDrawable(resources, bmp), fade = false)
                        }
                    }
                }
            }
            banner != null -> setArt(banner.fresh())
            else -> setArt(wallpaperDrawable())
        }
    }

    private fun setArt(d: Drawable?, fade: Boolean = true) {
        val target = if (currentTab == 0 && d != null) artAlpha else 0f
        heroArt.animate().cancel()
        if (!fade || prefs.animMs <= 0) {
            heroArt.setImageDrawable(d)
            heroArt.alpha = target
            return
        }
        heroArt.animate().alpha(0f).setDuration(110).withEndAction {
            heroArt.setImageDrawable(d)
            heroArt.animate().alpha(target).setDuration(240).start()
        }.start()
    }

    // ---------- แท็บและการเลื่อนโฟกัส ----------
    private fun selectTab(i: Int) {
        if (currentTab == i) return
        currentTab = i
        tabHome.isSelected = i == 0
        tabAll.isSelected = i == 1
        val show = if (i == 0) pageHome else gridAll
        val hide = if (i == 0) gridAll else pageHome
        hide.visibility = View.GONE
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(180).start()
        heroArt.animate().cancel()
        val target = if (i == 0 && heroArt.drawable != null) artAlpha else 0f
        heroArt.animate().alpha(target).setDuration(200).start()
    }

    private fun focusTab() {
        if (currentTab == 0) tabHome.requestFocus() else tabAll.requestFocus()
    }

    private fun focusPage() {
        if (currentTab == 1) {
            focusRow(gridAll, lastAll)
            return
        }
        when {
            rowFav.visibility == View.VISIBLE && favList.isNotEmpty() -> focusRow(rowFav, lastFav)
            rowApps.visibility == View.VISIBLE && appList.isNotEmpty() -> focusRow(rowApps, lastApp)
            else -> tabHome.requestFocus()
        }
    }

    private fun focusRow(rv: RecyclerView, pos: Int, tries: Int = 6) {
        val count = rv.adapter?.itemCount ?: 0
        if (count == 0) return
        val p = pos.coerceIn(0, count - 1)
        val vh = rv.findViewHolderForAdapterPosition(p)
        if (vh != null) {
            vh.itemView.requestFocus()
            return
        }
        rv.scrollToPosition(p)
        if (tries > 0) rv.postDelayed({ focusRow(rv, p, tries - 1) }, 40)
    }

    private fun focusPackage(pkg: String) {
        if (currentTab == 1) {
            val i = appList.indexOfFirst { it.packageName == pkg }
            focusRow(gridAll, if (i >= 0) i else lastAll)
            return
        }
        val f = favList.indexOfFirst { it.packageName == pkg }
        if (f >= 0 && rowFav.visibility == View.VISIBLE) {
            focusRow(rowFav, f)
            return
        }
        val a = appList.indexOfFirst { it.packageName == pkg }
        if (a >= 0 && rowApps.visibility == View.VISIBLE) {
            focusRow(rowApps, a)
            return
        }
        focusPage()
    }

    // กำหนดเส้นทางปุ่มทิศทางเอง เพื่อไม่ให้โฟกัสกระโดดไปผิดที่
    private fun onCardKey(row: Int, pos: Int, key: Int): Boolean {
        val up = key == KeyEvent.KEYCODE_DPAD_UP
        val down = key == KeyEvent.KEYCODE_DPAD_DOWN
        val left = key == KeyEvent.KEYCODE_DPAD_LEFT
        val right = key == KeyEvent.KEYCODE_DPAD_RIGHT

        if (row == ROW_FAV) {
            if (up) { focusTab(); return true }
            if (down) {
                if (rowApps.visibility == View.VISIBLE) focusRow(rowApps, lastApp)
                return true
            }
            if (left) return pos == 0
            if (right) return pos >= favList.size - 1
        } else if (row == ROW_APPS) {
            if (up) {
                if (rowFav.visibility == View.VISIBLE) focusRow(rowFav, lastFav) else focusTab()
                return true
            }
            if (down) return true
            if (left) return pos == 0
            if (right) return pos >= appList.size - 1
        } else {
            val cols = allColumns
            if (up && pos < cols) { focusTab(); return true }
            if (left) return pos % cols == 0
            if (right) return pos % cols == cols - 1 || pos >= appList.size - 1
            if (down) return pos / cols == (appList.size - 1) / cols
        }
        return false
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

    private fun setVisible(v: View, show: Boolean) {
        v.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun render() {
        val hidden = prefs.hidden
        val visible = apps.filter { it.packageName !in hidden }
        val byPkg = visible.associateBy { it.packageName }

        favList = if (prefs.showFavorites) prefs.favorites.mapNotNull { byPkg[it] } else emptyList()
        appList = if (prefs.sortMode == 1) {
            val counts = prefs.launchCounts()
            visible.sortedWith(
                compareByDescending<AppEntry> { counts[it.packageName] ?: 0 }
                    .thenBy(collator) { it.label }
            )
        } else {
            visible.sortedWith(compareBy(collator) { it.label })
        }

        val showFav = favList.isNotEmpty()
        val showApps = prefs.showAllApps || !showFav
        setVisible(favTitle, showFav)
        setVisible(rowFav, showFav)
        setVisible(appsTitle, showApps)
        setVisible(rowApps, showApps)

        favAdapter?.submit(favList)
        appsAdapter?.submit(if (showApps) appList else emptyList())
        allAdapter?.submit(appList)

        val target = pendingFocus
        pendingFocus = null
        if (target != null) {
            focusPackage(target)
        } else if (focusFirst) {
            focusPage()
        }
        focusFirst = false
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
        hiRes.evictAll()
        heroPkg = null
        pendingFocus = focusPkg
        if (focusPkg == null) focusFirst = true
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
        acts.add(Act("เปลี่ยนไอคอน / แบนเนอร์ (แนะนำ 1280×720)") {
            pickImage("รูปสำหรับ ${app.label} (แนะนำ 16:9 ความละเอียด 1280×720 ขึ้นไป)", ImageStore.iconKey(pkg)) {
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

    // ปุ่ม Back: ถ้าอยู่แท็บแอป ให้กลับมาแท็บสำหรับคุณ
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (currentTab == 1) {
            selectTab(0)
            focusPage()
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(heroRunnable)
        unregisterReceiver(packageReceiver)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }
}