package com.cleantv.launcher

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val label: String,
    val packageName: String,
    val banner: Drawable?,
    val icon: Drawable
)

class AppAdapter(
    private val span: Int,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private val items = ArrayList<AppEntry>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val banner: ImageView = view.findViewById(R.id.banner)
        val iconBox: View = view.findViewById(R.id.iconBox)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<AppEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)

        // ปรับความสูงการ์ดให้เป็นสัดส่วน 16:9 ตามความกว้างคอลัมน์จริง
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        val cellWidth = (parent.width - parent.paddingLeft - parent.paddingRight) / span
        if (cellWidth > 0) {
            lp.height = (cellWidth - lp.leftMargin - lp.rightMargin) * 9 / 16
        }

        view.clipToOutline = true // ตัดมุมโค้งให้ภาพแบนเนอร์

        // เอฟเฟกต์โฟกัส: ขยายขึ้นและลอยมีเงา
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1f
            v.animate()
                .scaleX(scale).scaleY(scale)
                .translationZ(if (hasFocus) 16f else 0f)
                .setDuration(140)
                .start()
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        val hasBanner = app.banner != null

        holder.banner.visibility = if (hasBanner) View.VISIBLE else View.GONE
        holder.iconBox.visibility = if (hasBanner) View.GONE else View.VISIBLE

        if (hasBanner) {
            holder.banner.setImageDrawable(app.banner)
        } else {
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
        }

        holder.itemView.contentDescription = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { onLongClick(app); true }
    }
}