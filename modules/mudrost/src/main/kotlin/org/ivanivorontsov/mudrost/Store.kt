package org.ivanivorontsov.mudrost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val query: String, val time: Long)

/** Favourites and history, kept locally in SharedPreferences (nothing leaves the device). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("mudrost", Context.MODE_PRIVATE)

    private fun ids(key: String): List<String> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    private fun saveIds(key: String, ids: List<String>) {
        prefs.edit().putString(key, JSONArray(ids).toString()).apply()
    }

    fun favEntries(): List<String> = ids("fav_entries")
    fun favQuotes(): List<String> = ids("fav_quotes")

    /** Returns the new state (true = now in favourites). Newest first. */
    fun toggleEntry(id: String): Boolean = toggle("fav_entries", id)
    fun toggleQuote(id: String): Boolean = toggle("fav_quotes", id)

    private fun toggle(key: String, id: String): Boolean {
        val cur = ids(key).toMutableList()
        val added = if (cur.remove(id)) false else { cur.add(0, id); true }
        saveIds(key, cur)
        return added
    }

    fun history(): List<HistoryItem> {
        val arr = JSONArray(prefs.getString("history", "[]"))
        return List(arr.length()) { i -> arr.getJSONObject(i).let { HistoryItem(it.getString("q"), it.getLong("t")) } }
    }

    fun addHistory(query: String, time: Long = System.currentTimeMillis()): List<HistoryItem> {
        val q = query.trim()
        if (q.isEmpty()) return history()
        val list = (listOf(HistoryItem(q, time)) + history().filter { !it.query.equals(q, ignoreCase = true) }).take(200)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("q", it.query).put("t", it.time)) }
        prefs.edit().putString("history", arr.toString()).apply()
        return list
    }

    fun removeHistory(item: HistoryItem): List<HistoryItem> {
        val list = history().filter { it != item }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("q", it.query).put("t", it.time)) }
        prefs.edit().putString("history", arr.toString()).apply()
        return list
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }
}
