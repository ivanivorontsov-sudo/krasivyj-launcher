package org.ivanivorontsov.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Support for standard (ADW/Nova-compatible) icon packs via their appfilter.xml. */
class IconPacks(private val context: Context) {
    data class Pack(val pkg: String, val label: String)

    private var loadedPkg: String? = null
    private var res: Resources? = null
    private var map: Map<String, String> = emptyMap()

    fun installed(): List<Pack> {
        val pm = context.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.teslacoilsw.launcher.THEME")
        return actions.flatMap { a ->
            try { pm.queryIntentActivities(Intent(a), 0) } catch (_: Exception) { emptyList() }
        }.map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }.distinctBy { it.pkg }.sortedBy { it.label }
    }

    @Synchronized
    private fun ensure(pkg: String) {
        if (loadedPkg == pkg) return
        loadedPkg = pkg; map = emptyMap(); res = null
        try {
            val r = context.packageManager.getResourcesForApplication(pkg)
            res = r
            val parser: XmlPullParser = run {
                @SuppressLint("DiscouragedApi") val id = r.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) r.getXml(id) else XmlPullParserFactory.newInstance().newPullParser().apply { setInput(r.assets.open("appfilter.xml"), "UTF-8") }
            }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) m[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = dr
                }
                ev = parser.next()
            }
            map = m
        } catch (_: Exception) { map = emptyMap() }
    }

    @SuppressLint("DiscouragedApi")
    fun drawable(pkg: String, componentPkg: String, componentCls: String, density: Int): Drawable? {
        ensure(pkg)
        val r = res ?: return null
        val name = map["$componentPkg/$componentCls"] ?: map["$componentPkg/${componentCls.removePrefix(componentPkg)}"] ?: return null
        return try {
            val id = r.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else r.getDrawableForDensity(id, density, null)
        } catch (_: Exception) { null }
    }
}
