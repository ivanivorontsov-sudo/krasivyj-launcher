package org.ivanivorontsov.launcher.core

/** Search in the widget picker: by widget label and app name (case/ё-insensitive, translit, wrong layout). */
object WidgetSearch {
    fun key(widgetLabel: String, appName: String, packageName: String = ""): SearchKey =
        SearchKey("$widgetLabel $appName", packageName)

    fun <T> filter(items: List<T>, query: String, keyOf: (T) -> SearchKey): List<T> =
        SearchEngine.search(items, query, keyOf, limit = Int.MAX_VALUE)
}
