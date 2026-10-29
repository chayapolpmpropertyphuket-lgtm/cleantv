package com.cleantv.launcher

import android.graphics.drawable.GradientDrawable

object Themes {

    class Preset(val name: String, val colors: IntArray)
    class Accent(val name: String, val color: Int)

    val PRESETS = listOf(
        Preset("ราตรีม่วง", intArrayOf(0xFF0B0F1A.toInt(), 0xFF111827.toInt(), 0xFF1E1B4B.toInt())),
        Preset("มหาสมุทร", intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())),
        Preset("ป่าลึก", intArrayOf(0xFF0B1D13.toInt(), 0xFF134E3A.toInt(), 0xFF1F6F50.toInt())),
        Preset("พระอาทิตย์ตก", intArrayOf(0xFF2D1B3D.toInt(), 0xFF6B2D5C.toInt(), 0xFFB8475A.toInt())),
        Preset("กราไฟต์", intArrayOf(0xFF111111.toInt(), 0xFF1C1C1E.toInt(), 0xFF2C2C2E.toInt())),
        Preset("ดำสนิท", intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()))
    )

    val ACCENTS = listOf(
        Accent("ขาว", 0xFFFFFFFF.toInt()),
        Accent("ฟ้า", 0xFF38BDF8.toInt()),
        Accent("ม่วง", 0xFFA78BFA.toInt()),
        Accent("เขียว", 0xFF34D399.toInt()),
        Accent("ส้ม", 0xFFFB923C.toInt()),
        Accent("ชมพู", 0xFFF472B6.toInt())
    )

    fun gradient(index: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            PRESETS[index.coerceIn(0, PRESETS.size - 1)].colors
        )

    fun accent(index: Int): Int = ACCENTS[index.coerceIn(0, ACCENTS.size - 1)].color
}