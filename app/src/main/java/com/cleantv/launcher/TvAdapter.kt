package com.cleantv.launcher

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// สร้างสำเนา Drawable เพื่อให้ใช้รูปเดียวกันในหลายจุดได้โดยไม่แสดงผลเพี้ยน
fun Drawable.fresh(): Drawable = constantState?.newDrawable()?.mutate() ?: this

class TvConfig(
    val circle: Boolean,     // true = ไอคอนวงกลม
    val iconOnly: Boolean,   // true = การ์ดแบบไอคอน (ไม่ใช้แบนเนอร์)
    val widthPx: Int,
    val heightPx: Int,
    val columns: Int,        // 0 = แถวแนวนอน, มากกว่า 0 = ตาราง
    val marginPx: Int,
    val showLabels: Boolean,
    val cornerPx: Float,
    val focusScale: Float,
    val accent: Int,
    val strokePx: Int,
    val animMs: Long
)

class TvAdapter(
    private val row: Int,
    private val cfg: TvConfig,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit,
    private val onFocus: (Int, Int, AppEntry) -> Unit,
    private val onKey: (Int, Int, Int) -> Boolean
) : RecyclerView.Adapter<TvAdapter.Holder>() {

    private val items = ArrayList<AppEntry>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView? = view.findViewById(R.id.image)
        val iconBox: View? = view.findViewById(R.id.iconBox)
        val icon: ImageView? = view.findViewById(R.id.icon)
        val label: TextView? = view.findViewById(R.id.label)
        val overlay: TextView? = view.findViewById(R.id.overlayLabel)
        val circleIcon: ImageView? = view.findViewById(R.id.circleIcon)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (cfg.circle) R.layout.item_circle else R.layout.item_app
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        val density = parent.resources.displayMetrics.density

        // ขนาดการ์ด
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        lp.setMargins(cfg.marginPx, cfg.marginPx, cfg.marginPx, cfg.marginPx)
        if (cfg.columns > 0) {
            val cell = (parent.width - parent.paddingLeft - parent.paddingRight) / cfg.columns
            val w = if (cell > 0) cell - cfg.marginPx * 2 else (160 * density).toInt()
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = if (cfg.iconOnly) w * 4 / 5 else w * 9 / 16
            if (cfg.iconOnly) {
                val icon = view.findViewById<ImageView>(R.id.icon)
                val size = lp.height * 9 / 20
                icon?.layoutParams = icon?.layoutParams?.apply { width = size; height = size }
            }
        } else {
            lp.width = cfg.widthPx
            lp.height = cfg.heightPx
        }
        view.layoutParams = lp

        // พื้นการ์ดและกรอบตอนถูกเลือก
        val kind = if (cfg.circle) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        view.background = GradientDrawable().apply {
            setShape(kind)
            setColor(0xFF1C2130.toInt())
            if (!cfg.circle) cornerRadius = cfg.cornerPx
        }
        val ring = GradientDrawable().apply {
            setShape(kind)
            setColor(Color.TRANSPARENT)
            if (!cfg.circle) cornerRadius = cfg.cornerPx
            setStroke(cfg.strokePx, cfg.accent)
        }
        view.foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ring)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        view.clipToOutline = true

        val holder = Holder(view)

        // เอฟเฟกต์ตอนเลือก: ขยายและลอยขึ้น
        view.setOnFocusChangeListener { v, has ->
            val s = if (has) cfg.focusScale else 1f
            val z = if (has) 12f * density else 0f
            v.animate().cancel()
            if (cfg.animMs <= 0L) {
                v.scaleX = s
                v.scaleY = s
                v.translationZ = z
            } else {
                v.animate().scaleX(s).scaleY(s).translationZ(z).setDuration(cfg.animMs).start()
            }
            if (has) {
                val p = holder.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION && p < items.size) onFocus(row, p, items[p])
            }
        }

        // ส่งปุ่มทิศทางให้หน้าหลักตัดสินใจว่าจะย้ายไปแถวไหน
        view.setOnKeyListener { _, keyCode, event ->
            val p = holder.bindingAdapterPosition
            event.action == KeyEvent.ACTION_DOWN && p != RecyclerView.NO_POSITION && onKey(row, p, keyCode)
        }
        return holder
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val app = items[position]
        if (cfg.circle) {
            h.circleIcon?.setImageDrawable((app.customImage ?: app.icon).fresh())
        } else {
            val pic = if (cfg.iconOnly) null else (app.customImage ?: app.banner)
            if (pic != null) {
                h.image?.visibility = View.VISIBLE
                h.image?.setImageDrawable(pic.fresh())
                h.iconBox?.visibility = View.GONE
                h.overlay?.text = app.label
                h.overlay?.visibility = if (cfg.showLabels) View.VISIBLE else View.GONE
            } else {
                h.image?.visibility = View.GONE
                h.overlay?.visibility = View.GONE
                h.iconBox?.visibility = View.VISIBLE
                h.icon?.setImageDrawable((app.customImage ?: app.icon).fresh())
                h.label?.text = app.label
            }
        }
        h.itemView.contentDescription = app.label
        h.itemView.setOnClickListener { onClick(app) }
        h.itemView.setOnLongClickListener {
            onLongClick(app)
            true
        }
    }
}