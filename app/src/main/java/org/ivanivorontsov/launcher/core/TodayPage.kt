package org.ivanivorontsov.launcher.core

/**
 * Turning the «Сегодня» side screen off from the screen itself (not via settings).
 * The previous config is kept so «Отменить» can restore the page and its widgets.
 */
data class TodayPageChange(
    val previous: LauncherConfig,
    val next: LauncherConfig,
) {
    val changed: Boolean get() = previous.todayPage && !next.todayPage
    fun undo(): LauncherConfig = previous
}

object TodayPage {
    const val UNDO_MS = 5000L
    const val REMOVED_MESSAGE = "Экран убран"
    const val UNDO_ACTION = "Отменить"

    /**
     * Disable the side screen immediately. Android widgets on it are moved to the end of
     * [themeId]'s home pages by default, so they are not lost; settings can still drop them.
     */
    fun remove(
        config: LauncherConfig,
        themeId: String,
        columns: Int,
        rows: Int,
        moveToHome: Boolean = true,
    ): TodayPageChange {
        if (!config.todayPage) return TodayPageChange(config, config)
        return TodayPageChange(config, config.disableTodayPage(themeId, columns, rows, moveToHome))
    }
}
