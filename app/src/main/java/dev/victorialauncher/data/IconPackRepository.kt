// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

class IconPackRepository(private val context: Context) {

    data class IconPackInfo(val packageName: String, val label: String)

    private val discoveryIntents = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
    )

    fun getInstalledIconPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = LinkedHashMap<String, IconPackInfo>()
        for (action in discoveryIntents) {
            @Suppress("DEPRECATION")
            val resolveInfos = pm.queryIntentActivities(Intent(action), 0)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (!packs.containsKey(pkg)) {
                    packs[pkg] = IconPackInfo(pkg, ri.loadLabel(pm)?.toString() ?: pkg)
                }
            }
        }
        return packs.values.toList()
    }

    /**
     * The parsed pack, held as one immutable value.
     *
     * This used to be three separate fields assigned one after another. `warmIconCache` runs
     * on a background dispatcher while the UI thread resolves icons, so a reader could see
     * the new package name while the map was still the previous pack's — and every icon then
     * silently fell back to the default. One volatile write of one object can't tear.
     */
    private class PackCache(
        val packPackage: String,
        val map: Map<String, String>,
        val resources: Resources?,
    )

    @Volatile
    private var cache: PackCache? = null

    private fun load(packPackage: String): PackCache {
        cache?.takeIf { it.packPackage == packPackage }?.let { return it }
        val pm = context.packageManager
        val map = mutableMapOf<String, String>()
        var res: Resources? = null
        try {
            res = pm.getResourcesForApplication(packPackage)
            val xmlResId = res.getIdentifier("appfilter", "xml", packPackage)
            if (xmlResId != 0) {
                val parser = res.getXml(xmlResId)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            val key = component
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")
                            map[key] = drawable
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            // Malformed or unreadable icon pack; fall back to default icons for everything.
        }
        return PackCache(packPackage, map, res).also { cache = it }
    }

    /**
     * Every drawable the pack offers, for the manual "pick an icon" grid. Packs list these in
     * `drawable.xml`; when that's missing we fall back to the distinct drawables named by
     * `appfilter.xml`.
     */
    fun getPackIcons(packPackage: String): List<String> {
        val names = LinkedHashSet<String>()
        try {
            val res = context.packageManager.getResourcesForApplication(packPackage)
            val drawableXml = res.getIdentifier("drawable", "xml", packPackage)
            if (drawableXml != 0) {
                val parser = res.getXml(drawableXml)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        parser.getAttributeValue(null, "drawable")?.let { names += it }
                    }
                    eventType = parser.next()
                }
            }
            if (names.isEmpty()) {
                names += load(packPackage).map.values
            }
        } catch (e: Exception) {
            // Unreadable pack; return whatever we managed to collect.
        }
        return names.toList()
    }

    fun loadPackDrawable(packPackage: String, drawableName: String): Drawable? {
        return try {
            val res = context.packageManager.getResourcesForApplication(packPackage)
            val resId = res.getIdentifier(drawableName, "drawable", packPackage)
            if (resId == 0) null else res.getDrawable(resId, null)
        } catch (e: Exception) {
            null
        }
    }

    fun getIcon(packPackage: String?, componentName: ComponentName, fallback: () -> Drawable): Drawable {
        if (packPackage.isNullOrEmpty()) return fallback()
        return try {
            val pack = load(packPackage)
            val drawableName = pack.map[componentName.flattenToString()] ?: return fallback()
            val res = pack.resources ?: return fallback()
            val resId = res.getIdentifier(drawableName, "drawable", packPackage)
            if (resId == 0) return fallback()
            res.getDrawable(resId, null) ?: fallback()
        } catch (e: Exception) {
            fallback()
        }
    }
}