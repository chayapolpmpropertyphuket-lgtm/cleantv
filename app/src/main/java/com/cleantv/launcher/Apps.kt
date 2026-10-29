package com.cleantv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val originalLabel: String,
    val banner: Drawable?,
    val icon: Drawable,
    val customImage: Drawable?
)

object AppLoader {

    fun load(ctx: Context, prefs: Prefs): List<AppEntry> {
        val pm = ctx.packageManager
        val names = prefs.customNames()
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue

                val original = ri.loadLabel(pm).toString()
                val custom = ImageStore.load(ctx, ImageStore.iconKey(pkg), 480, 270)
                    ?.let { BitmapDrawable(ctx.resources, it) }

                out.add(
                    AppEntry(
                        packageName = pkg,
                        label = names[pkg] ?: original,
                        originalLabel = original,
                        banner = ri.activityInfo.loadBanner(pm),
                        icon = ri.loadIcon(pm),
                        customImage = custom
                    )
                )
            }
        }
        return out
    }
}