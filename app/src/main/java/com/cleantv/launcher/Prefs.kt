package com.cleantv.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.getSharedPreferences("cleantv", Context.MODE_PRIVATE)

    private inner class IntPref(private val key: String, private val def: Int) :
        ReadWriteProperty<Prefs, Int> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>): Int =
            sp.getInt(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Int) {
            sp.edit().putInt(key, value).apply()
        }
    }

    private inner class BoolPref(private val key: String, private val def: Boolean) :
        ReadWriteProperty<Prefs, Boolean> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>): Boolean =
            sp.getBoolean(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private inner class StrPref(private val key: String, private val def: String) :
        ReadWriteProperty<Prefs, String> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>): String =
            sp.getString(key, def) ?: def
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: String) {
            sp.edit().putString(key, value).apply()
        }
    }

    // ---------- พื้นหลัง ----------
    var wallpaperMode by IntPref("wallpaper_mode", 0)   // 0 = ธีมสี, 1 = รูปภาพ
    var presetIndex by IntPref("preset", 0)
    var dimPercent by IntPref("dim", 30)
    var heroSource by IntPref("hero_source", 0)         // 0 = ตามแอปที่เลือก, 1 = รูปพื้นหลังของฉัน


    // ---------- ส่วนหัว ----------
    var logoMode by IntPref("logo_mode", 1)             // 0 = ไม่แสดง, 1 = ข้อความ, 2 = รูปภาพ
    var logoText by StrPref("logo_text", "CleanTV")
    var showGreeting by BoolPref("show_greeting", true)
    var showClock by BoolPref("show_clock", true)
    var use24h by BoolPref("use_24h", true)
    var showDate by BoolPref("show_date", true)

    // ---------- การ์ดแอป ----------
    var cardStyle by IntPref("card_style", 0)           // 0 = แบนเนอร์, 1 = ไอคอน
    var columns by IntPref("columns", 5)
    var showLabels by BoolPref("show_labels", false)
    var cornerDp by IntPref("corner", 14)
    var focusScale by IntPref("focus_scale", 110)
    var accentIndex by IntPref("accent", 0)
    var animMs by IntPref("anim_ms", 140)

    // ---------- การจัดเรียง ----------
    var showFavorites by BoolPref("show_favorites", true)
    var showAllApps by BoolPref("show_all", true)
    var sortMode by IntPref("sort", 0)                  // 0 = ชื่อ, 1 = ใช้บ่อย

    var favorites: List<String>
        get() = sp.getString("favorites", null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: DEFAULT_FAVORITES
        set(value) {
            sp.edit().putString("favorites", value.joinToString(",")).apply()
        }

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        set(value) {
            sp.edit().putStringSet("hidden", HashSet(value)).apply()
        }

    private fun readJson(key: String): JSONObject =
        try {
            JSONObject(sp.getString(key, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    fun customNames(): Map<String, String> {
        val o = readJson("names")
        val map = HashMap<String, String>()
        for (k in o.keys()) map[k] = o.optString(k)
        return map
    }

    fun setCustomName(pkg: String, name: String?) {
        val o = readJson("names")
        if (name.isNullOrBlank()) o.remove(pkg) else o.put(pkg, name.trim())
        sp.edit().putString("names", o.toString()).apply()
    }

    fun clearCustomNames() {
        sp.edit().remove("names").apply()
    }

    fun launchCounts(): Map<String, Int> {
        val o = readJson(KEY_COUNTS)
        val map = HashMap<String, Int>()
        for (k in o.keys()) map[k] = o.optInt(k)
        return map
    }

    fun countLaunch(pkg: String) {
        val o = readJson(KEY_COUNTS)
        o.put(pkg, o.optInt(pkg) + 1)
        sp.edit().putString(KEY_COUNTS, o.toString()).apply()
    }

    // แจ้งหน้าหลักให้โหลดใหม่ (ใช้ตอนเปลี่ยนรูปภาพ)
    fun touch() {
        sp.edit().putLong("stamp", System.currentTimeMillis()).apply()
    }

    fun resetAll() {
        sp.edit().clear().apply()
        touch()
    }

    companion object {
        const val KEY_COUNTS = "counts"
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus"
        )
    }
}