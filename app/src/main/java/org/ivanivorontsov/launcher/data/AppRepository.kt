package org.ivanivorontsov.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.core.AlphabetIndex
import org.ivanivorontsov.launcher.core.AppCategory
import org.ivanivorontsov.launcher.core.Categorizer
import org.ivanivorontsov.launcher.core.SearchKey
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.core.TileSize
import java.text.Collator
import java.util.Locale

@Immutable
class AppInfo(
    val key: String,
    val label: String,
    val originalLabel: String,
    val pkg: String,
    val cls: String,
    val user: UserHandle,
    val isWork: Boolean,
    val category: AppCategory,
    val letter: String,
    val search: SearchKey,
) {
    val component: ComponentName get() = ComponentName(pkg, cls)
    override fun equals(other: Any?) = other is AppInfo && other.key == key && other.label == label
    override fun hashCode() = key.hashCode() * 31 + label.hashCode()
}

class AppRepository(private val context: Context, private val store: Store) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private var reloadJob: Job? = null
    private val collator = Collator.getInstance(Locale("ru")).apply { strength = Collator.PRIMARY }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = scheduleReload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = scheduleReload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE); addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED); addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) = scheduleReload() }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
        scope.launch { store.config.collect { cfg -> relabel(cfg.renamed) } }
        scheduleReload(0)
    }

    private var lastRenamed: Map<String, String> = emptyMap()
    private fun relabel(renamed: Map<String, String>) {
        if (renamed == lastRenamed) return
        lastRenamed = renamed
        if (_loaded.value) scheduleReload(0)
    }

    fun scheduleReload(delayMs: Long = 250) {
        reloadJob?.cancel()
        reloadJob = scope.launch { delay(delayMs); load() }
    }

    fun keyFor(component: ComponentName, user: UserHandle): String =
        component.flattenToShortString() + "#" + userManager.getSerialNumberForUser(user)

    private fun load() {
        val me = Process.myUserHandle()
        val renamed = store.config.value.renamed
        val list = ArrayList<AppInfo>(200)
        for (user in userManager.userProfiles) {
            val acts: List<LauncherActivityInfo> = try { launcherApps.getActivityList(null, user) } catch (_: Exception) { emptyList() }
            for (a in acts) {
                if (a.componentName.packageName == context.packageName && user == me) continue
                val key = keyFor(a.componentName, user)
                val orig = a.label?.toString()?.trim().orEmpty().ifEmpty { a.componentName.packageName }
                val label = renamed[key] ?: orig
                val cat = Categorizer.categorize(a.componentName.packageName, orig, if (Build.VERSION.SDK_INT >= 26) a.applicationInfo.category else -1)
                list += AppInfo(key, label, orig, a.componentName.packageName, a.componentName.className, user, user != me, cat,
                    AlphabetIndex.letterOf(label), SearchKey(label, if (label != orig) orig + " " + a.componentName.packageName else a.componentName.packageName))
            }
        }
        list.sortWith(compareBy<AppInfo> { AlphabetIndex.order(it.letter) }.thenComparator { a, b -> collator.compare(a.label, b.label) })
        _apps.value = list
        _loaded.value = true
        val keys = list.mapTo(HashSet()) { it.key }
        if (list.isNotEmpty()) store.update { it.prune(keys) }
        seedIfNeeded(list)
    }

    fun find(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }

    /** First run: fill dock, home and tiles with sensible defaults (phone, messages, browser, camera…). */
    private fun seedIfNeeded(list: List<AppInfo>) {
        if (store.config.value.seeded || list.isEmpty()) return
        val pm = context.packageManager
        fun pkgFor(intent: Intent): String? = try { pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.takeIf { it != "android" } } catch (_: Exception) { null }
        fun byPkg(p: String?): AppInfo? = p?.let { pk -> list.firstOrNull { it.pkg == pk && !it.isWork } }
        val dialer = byPkg(pkgFor(Intent(Intent.ACTION_DIAL)))
        val sms = byPkg(pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)))
        val browser = byPkg(pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)))
        val camera = byPkg(pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)))
        val gallery = byPkg(pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)))
        val clock = byPkg(pkgFor(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        val settings = list.firstOrNull { it.pkg == "com.android.settings" }
        val dock = listOfNotNull(dialer, sms, browser, camera).distinctBy { it.key }.take(4)
        val popular = listOf("telegram", "whatsapp", "vkontakte", "youtube", "sber", "tinkoff", "tbank", "ozon", "wildberries", "maps", "navi", "music", "calendar", "calculator", "vending", "gmail", "yandex", "2gis", "notes", "files", "weather")
        val home = ArrayList<AppInfo>()
        listOfNotNull(gallery, clock, settings).forEach { if (it !in dock) home += it }
        for (w in popular) list.firstOrNull { it.pkg.contains(w) && it !in dock && it !in home && !it.isWork }?.let { home += it }
        for (a in list) { if (home.size >= 16) break; if (a !in dock && a !in home && !a.isWork) home += a }
        val tiles = ArrayList<TileItem>()
        tiles += TileItem("clock", TileKind.CLOCK, size = TileSize.WIDE)
        listOfNotNull(dialer, sms).forEach { tiles += TileItem("t_" + it.key, TileKind.APP, it.key, TileSize.MEDIUM) }
        tiles += TileItem("photos", TileKind.PHOTOS, size = TileSize.MEDIUM)
        listOfNotNull(browser, camera, settings, gallery).distinctBy { it.key }.forEach { tiles += TileItem("t_" + it.key, TileKind.APP, it.key, TileSize.SMALL) }
        tiles += TileItem("calendar", TileKind.CALENDAR, size = TileSize.MEDIUM)
        home.take(6).forEachIndexed { i, a -> tiles += TileItem("t_" + a.key, TileKind.APP, a.key, if (i == 2) TileSize.WIDE else TileSize.MEDIUM) }
        store.update { c ->
            c.copy(seeded = true, dock = dock.map { it.key }, home = home.map { it.key },
                minimalFavorites = (dock + home).distinctBy { it.key }.take(7).map { it.key }, tiles = tiles.distinctBy { it.id })
        }
    }

    // ---- shortcuts (only available while we are the default launcher) ----
    fun hasShortcutPermission(): Boolean = try { launcherApps.hasShortcutHostPermission() } catch (_: Exception) { false }

    fun shortcuts(app: AppInfo): List<ShortcutInfo> {
        if (!hasShortcutPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery().setPackage(app.pkg).setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            (launcherApps.getShortcuts(q, app.user) ?: emptyList()).filter { it.isEnabled }.sortedBy { it.rank }.take(5)
        } catch (_: Exception) { emptyList() }
    }

    val launcher: LauncherApps get() = launcherApps
}
