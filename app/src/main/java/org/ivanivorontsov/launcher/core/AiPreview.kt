package org.ivanivorontsov.launcher.core

/** Combined preview of an arrangement and/or setting changes, ready to confirm. */
data class AiPreview(
    val arrangement: AiLayout.Application?,
    val settings: AiSettings.Proposal,
    val layout: ScreenLayout,
    val usedLocalFallback: Boolean = false,
) {
    val hasWork: Boolean
        get() = arrangement != null || settings.changes.isNotEmpty() || settings.unsupported.isNotEmpty() || settings.widget != null
}

object AiComposer {
    fun compose(
        prompt: String,
        apps: List<AiLayout.CatalogApp>,
        current: ScreenLayout,
        config: LauncherConfig,
        theme: ThemeSpec,
        modelText: String? = null,
        maxDock: Int = 5,
        columns: Int = 4,
        rows: Int = 6,
    ): AiPreview {
        var arrangement: AiLayout.Application? = null
        var modelSettings = emptyList<AiSettings.Input>()
        var modelUnsupported = emptyList<String>()
        var usedLocal = false
        if (!modelText.isNullOrBlank()) {
            try {
                val req = AiLayout.parseRequest(modelText)
                modelSettings = req.settings
                modelUnsupported = req.unsupported
                if (req.hasArrangement || AiLayout.wantsArrangement(prompt)) {
                    arrangement = AiLayout.applyPlan(current, req.plan, apps, maxDock, columns, rows, prompt)
                }
            } catch (_: Exception) {
                usedLocal = true
            }
        }
        if (arrangement == null && AiLayout.wantsArrangement(prompt)) {
            arrangement = AiLayout.localFallback(apps, current, maxDock, columns, rows, prompt)
            usedLocal = usedLocal || modelText.isNullOrBlank()
        }
        val (localIn, localUn) = AiSettings.detectLocal(prompt)
        val merged = LinkedHashMap<String, AiSettings.Input>()
        for (i in localIn) merged[i.key.lowercase()] = i
        for (i in modelSettings) merged[i.key.lowercase()] = i
        val settings = AiSettings.validate(merged.values.toList(), config, theme, localUn + modelUnsupported)
        var layout = arrangement?.layout ?: current
        settings.widget?.let { layout = AiSettings.applyWidget(layout, it, theme.columns.coerceIn(3, 6), theme.rows.coerceIn(3, 8)) }
        return AiPreview(arrangement = arrangement, settings = settings, layout = layout, usedLocalFallback = usedLocal)
    }

    /** Config to write on confirm: settings already applied, layout slotted into [themeId]. */
    fun configToApply(preview: AiPreview, themeId: String, appKeys: List<String> = emptyList()): LauncherConfig {
        var cfg = preview.settings.nextConfig.editLayout(themeId) { preview.layout }
        val turnedOn = preview.settings.nextConfig.allAppsOnHome &&
            preview.settings.changes.any { it.key == "allAppsOnHome" && it.to == "вкл" }
        if (turnedOn && appKeys.isNotEmpty()) cfg = AiLayout.enableAllAppsOnHome(cfg, themeId, appKeys)
        return cfg
    }
}
