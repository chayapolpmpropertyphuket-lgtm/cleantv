package com.cleantv.launcher

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class HomeItem {
    class Header(val title: String) : HomeItem()
    class App(val app: AppEntry) : HomeItem()
}

class CardConfig(
    val columns: Int,
    val style: Int,          // 0 = แบนเนอร์ 16:9, 1 = ไอคอน
    val showLabels: Boolean,
    val cornerPx: Float,
    val focusScale: Float,
    val accent: Int,
    val animMs: Long,
    val strokePx: Int
)

class HomeAdapter(
    private val cfg: CardConfig,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }

    private val items = ArrayList<HomeItem>()

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view as TextView
    }

    class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val iconBox: View = view.findViewById(R.id.iconBox)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
        val overlay: TextView = view.findViewById(R.id.overlayLabel)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<HomeItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun isHeader(position: Int) = items.getOrNull(position) is HomeItem.Header
    fun firstAppPosition() = items.indexOfFirst { it is HomeItem.App }
    fun positionOf(pkg: String) =
        items.indexOfFirst { it is HomeItem.App && it.app.packageName == pkg }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) =
        if (items[position] is HomeItem.Header) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
        }
        val view = inflater.inflate(R.layout.item_app, parent, false)

        // คำนวณขนาดการ์ดจากความกว้างจอและจำนวนคอลัมน์
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        val cell = (parent.width - parent.paddingLeft - parent.paddingRight) / cfg.columns
        if (cell > 0) {
            val w = cell - lp.leftMargin - lp.rightMargin
            lp.height = if (cfg.style == 0) w * 9 / 16 else w * 4 / 5
            if (cfg.style == 1) {
                val icon = view.findViewById<ImageView>(R.id.icon)
                val size = lp.height * 9 / 20
                icon.layoutParams = icon.layoutParams.apply { width = size; height = size }
            }
        }
        view.layoutParams = lp

        // พื้นการ์ดและกรอบตอนถูกเลือก (ปรับตามการตั้งค่า)
        view.background = GradientDrawable().apply {
            setColor(0xCC1C2238.toInt())
            cornerRadius = cfg.cornerPx
        }
        val focusedBorder = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = cfg.cornerPx
            setStroke(cfg.strokePx, cfg.accent)
        }
        view.foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedBorder)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        view.clipToOutline = true

        view.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) cfg.focusScale else 1f
            val z = if (hasFocus) 16f else 0f
            if (cfg.animMs <= 0L) {
                v.scaleX = s
                v.scaleY = s
                v.translationZ = z
            } else {
                v.animate().scaleX(s).scaleY(s).translationZ(z).setDuration(cfg.animMs).start()
            }
        }
        return AppHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (item is HomeItem.Header) {
            (holder as HeaderHolder).title.text = item.title
            return
        }
        val app = (item as HomeItem.App).app
        val h = holder as AppHolder

        val picture: Drawable? = if (cfg.style == 0) (app.customImage ?: app.banner) else null
        if (picture != null) {
            h.image.visibility = View.VISIBLE
            h.iconBox.visibility = View.GONE
            h.image.setImageDrawable(picture)
            h.overlay.text = app.label
            h.overlay.visibility = if (cfg.showLabels) View.VISIBLE else View.GONE
        } else {
            h.image.visibility = View.GONE
            h.overlay.visibility = View.GONE
            h.iconBox.visibility = View.VISIBLE
            h.icon.setImageDrawable(app.customImage ?: app.icon)
            h.label.text = app.label
        }

        h.itemView.contentDescription = app.label
        h.itemView.setOnClickListener { onClick(app) }
        h.itemView.setOnLongClickListener {
            onLongClick(app)
            true
        }
    }
}