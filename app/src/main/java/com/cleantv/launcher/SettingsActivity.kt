package com.cleantv.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : BaseActivity() {

    class Row(
        val title: String,
        val summary: (() -> String)? = null,
        val action: (() -> Unit)? = null      // null = หัวข้อหมวด
    )

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val summary: TextView? = view.findViewById(R.id.summary)
    }

    inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows[position].action == null) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val layout = if (viewType == 0) R.layout.item_setting_header else R.layout.item_setting
            return RowHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = rows[position]
            holder.title.text = row.title
            holder.summary?.let { s ->
                val text = row.summary?.invoke().orEmpty()
                s.text = text
                s.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
            val act = row.action
            if (act != null) holder.itemView.setOnClickListener { act() }
        }
    }

    private val rows = ArrayList<Row>()
    private val adapter = RowAdapter()
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.itemAnimator = null
        buildRows()
        list.adapter = adapter
        list.post { list.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus() }
    }

    private fun refresh() {
        adapter.notifyItemRangeChanged(0, rows.size)
    }

    // ---------- ตัวช่วยสร้างแถว ----------
    private fun section(title: String) {
        rows.add(Row(title))
    }

    private fun action(title: String, summary: () -> String = { "" }, run: () -> Unit) {
        rows.add(Row(title, summary, run))
    }

    private fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        rows.add(Row(title, { if (get()) "เปิด" else "ปิด" }) {
            set(!get())
            refresh()
        })
    }

    private fun choice(
        title: String,
        labels: List<String>,
        values: List<Int>,
        get: () -> Int,
        set: (Int) -> Unit
    ) {
        rows.add(Row(title, { labels.getOrElse(values.indexOf(get())) { "กำหนดเอง" } }) {
            choose(title, labels, values.indexOf(get())) { i ->
                set(values[i])
                refresh()
            }
        })
    }

    // ---------- รายการตั้งค่าทั้งหมด ----------
    private fun buildRows() {
        section("หน้าหลักแบบ Google TV")
        choice(
            "ภาพด้านบน (Hero)",
            listOf("เปลี่ยนตามแอปที่เลือก", "ใช้รูปพื้นหลังของฉันตลอด"),
            listOf(0, 1),
            { prefs.heroSource }
        ) { prefs.heroSource = it }

        section("พื้นหลัง")
        choice("ประเภทพื้นหลัง", listOf("ธีมสี", "รูปภาพ"), listOf(0, 1), { prefs.wallpaperMode }) { v ->
            if (v == 1 && !ImageStore.exists(this, ImageStore.WALLPAPER)) {
                toast("ยังไม่มีรูปพื้นหลัง ให้กด 'เลือกรูปพื้นหลัง' ก่อน")
            }
            prefs.wallpaperMode = v
        }
        choice(
            "ธีมสี",
            Themes.PRESETS.map { it.name },
            Themes.PRESETS.indices.toList(),
            { prefs.presetIndex }
        ) {
            prefs.presetIndex = it
            prefs.wallpaperMode = 0
        }
        action("เลือกรูปพื้นหลัง", {
            if (ImageStore.exists(this, ImageStore.WALLPAPER)) "มีรูปแล้ว (กดเพื่อเปลี่ยน)" else "ยังไม่มีรูป"
        }) {
            pickImage("รูปพื้นหลัง (แนะนำ 1920×1080)", ImageStore.WALLPAPER) {
                prefs.wallpaperMode = 1
                refresh()
            }
        }
        action("ลบรูปพื้นหลัง") {
            ImageStore.delete(this, ImageStore.WALLPAPER)
            prefs.wallpaperMode = 0
            prefs.touch()
            toast("ลบรูปพื้นหลังแล้ว")
            refresh()
        }
        choice(
            "ความมืดของรูปพื้นหลัง",
            (0..8).map { "${it * 10}%" },
            (0..8).map { it * 10 },
            { prefs.dimPercent }
        ) { prefs.dimPercent = it }

        section("โลโก้และส่วนหัว")
        choice("รูปแบบโลโก้", listOf("ไม่แสดง", "ข้อความ", "รูปภาพ"), listOf(0, 1, 2), { prefs.logoMode }) {
            prefs.logoMode = it
        }
        action("ข้อความโลโก้", { prefs.logoText }) {
            askText("ข้อความโลโก้", prefs.logoText, "เช่น ทีวีบ้านเรา") {
                prefs.logoText = it
                prefs.logoMode = 1
                refresh()
            }
        }
        action("เลือกรูปโลโก้", {
            if (ImageStore.exists(this, ImageStore.LOGO)) "มีรูปแล้ว (กดเพื่อเปลี่ยน)"
            else "ยังไม่มีรูป (แนะนำ PNG พื้นหลังโปร่งใส)"
        }) {
            pickImage("รูปโลโก้ (แนะนำ PNG กว้าง 600–800 px)", ImageStore.LOGO) {
                prefs.logoMode = 2
                refresh()
            }
        }
        toggle("แสดงคำทักทาย", { prefs.showGreeting }) { prefs.showGreeting = it }
        toggle("แสดงนาฬิกา", { prefs.showClock }) { prefs.showClock = it }
        choice(
            "รูปแบบเวลา",
            listOf("24 ชั่วโมง (21:30)", "12 ชั่วโมง (9:30 หลังเที่ยง)"),
            listOf(1, 0),
            { if (prefs.use24h) 1 else 0 }
        ) { prefs.use24h = it == 1 }
        toggle("แสดงวันที่", { prefs.showDate }) { prefs.showDate = it }

        section("การ์ดแอป")
        choice("รูปแบบการ์ด", listOf("แบนเนอร์ 16:9", "ไอคอน"), listOf(0, 1), { prefs.cardStyle }) {
            prefs.cardStyle = it
        }
        choice("จำนวนคอลัมน์", (3..8).map { "$it คอลัมน์" }, (3..8).toList(), { prefs.columns }) {
            prefs.columns = it
        }
        toggle("แสดงชื่อแอปบนแบนเนอร์", { prefs.showLabels }) { prefs.showLabels = it }
        choice(
            "ความโค้งของมุม",
            listOf("เหลี่ยม", "น้อย", "ปานกลาง", "มาก", "มากที่สุด"),
            listOf(0, 6, 14, 20, 28),
            { prefs.cornerDp }
        ) { prefs.cornerDp = it }
        choice(
            "ขนาดการ์ดเมื่อถูกเลือก",
            listOf("100% (ไม่ขยาย)", "105%", "110%", "115%", "120%"),
            listOf(100, 105, 110, 115, 120),
            { prefs.focusScale }
        ) { prefs.focusScale = it }
        choice(
            "สีกรอบเมื่อถูกเลือก",
            Themes.ACCENTS.map { it.name },
            Themes.ACCENTS.indices.toList(),
            { prefs.accentIndex }
        ) { prefs.accentIndex = it }
        choice(
            "ความเร็วแอนิเมชัน",
            listOf("ปิด (เร็วที่สุด)", "เร็ว", "ปกติ", "นุ่มนวล"),
            listOf(0, 90, 140, 220),
            { prefs.animMs }
        ) { prefs.animMs = it }

        section("การจัดเรียงแอป")
        toggle("แสดงแถวรายการโปรด", { prefs.showFavorites }) { prefs.showFavorites = it }
        toggle("แสดงแอปทั้งหมด", { prefs.showAllApps }) { prefs.showAllApps = it }
        choice(
            "เรียงแอปทั้งหมดตาม",
            listOf("ชื่อ (ก–ฮ, A–Z)", "ใช้บ่อยที่สุด"),
            listOf(0, 1),
            { prefs.sortMode }
        ) { prefs.sortMode = it }
        action("แอปที่ซ่อนไว้", { "${prefs.hidden.size} แอป" }) { manageHidden() }
        action("ล้างรายการโปรด") {
            confirm("นำแอปทั้งหมดออกจากรายการโปรด?") {
                prefs.favorites = emptyList()
                refresh()
            }
        }

        section("ระบบ")
        action("เปิดการตั้งค่าของทีวี") { safeStart(Intent(Settings.ACTION_SETTINGS)) }
        action("ล้างชื่อและไอคอนที่กำหนดเองทั้งหมด") {
            confirm("ล้างชื่อและไอคอนที่เปลี่ยนไว้ทั้งหมด?") {
                prefs.clearCustomNames()
                ImageStore.deleteAllIcons(this)
                prefs.touch()
                toast("ล้างเรียบร้อย")
            }
        }
        action("รีเซ็ตการตั้งค่าทั้งหมด") {
            confirm("คืนค่าทุกอย่างเป็นค่าเริ่มต้น (รวมรูปพื้นหลังและโลโก้)?") {
                ImageStore.delete(this, ImageStore.WALLPAPER)
                ImageStore.delete(this, ImageStore.LOGO)
                ImageStore.deleteAllIcons(this)
                prefs.resetAll()
                toast("รีเซ็ตเรียบร้อย")
                refresh()
            }
        }
        action("เกี่ยวกับ", { "CleanTV เวอร์ชัน ${versionName()}" }) {
            toast("CleanTV — Launcher ส่วนตัว ไม่มีโฆษณา ไม่เก็บข้อมูล")
        }
    }

    private fun manageHidden() {
        val hidden = prefs.hidden.toList()
        if (hidden.isEmpty()) {
            toast("ยังไม่มีแอปที่ซ่อนไว้ (ซ่อนได้โดยกดค้างที่แอปบนหน้าหลัก)")
            return
        }
        val labels = hidden.map { pkg ->
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
        val checked = BooleanArray(hidden.size) { true }
        dialog().setTitle("เอาเครื่องหมายถูกออกเพื่อแสดงแอปอีกครั้ง")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, i, isChecked -> checked[i] = isChecked }
            .setPositiveButton("บันทึก") { _, _ ->
                prefs.hidden = hidden.filterIndexed { i, _ -> checked[i] }.toSet()
                refresh()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun versionName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
}