package com.maeilmail.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 최근 검색어. 이 기기에만 둔다 (계정·웹과 나누지 않는다).
 * 30일이 지난 것은 저절로 지우고, 많아도 20개까지만 둔다. 같은 검색어를 다시 찾으면 맨 앞으로 옮긴다.
 */
class SearchHistory private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    /** text: 검색어, at: 마지막으로 찾은 시각 (epoch ms) */
    data class Entry(val text: String, val at: Long)

    private val _entries = MutableStateFlow(read())
    /** 최근에 찾은 것부터 */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(text: String) = update { merge(it, text, System.currentTimeMillis()) }

    fun remove(text: String) = update { list -> list.filter { it.text != text } }

    fun clear() = update { emptyList() }

    /** 오래된 것을 지운다 (검색 화면을 열 때) */
    fun prune() = update { prune(it, System.currentTimeMillis()) }

    private fun update(change: (List<Entry>) -> List<Entry>) {
        val next = change(_entries.value)
        if (next == _entries.value) return
        _entries.value = next
        val json = JSONArray(next.map { JSONObject().put("text", it.text).put("at", it.at) })
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun read(): List<Entry> = runCatching {
        val a = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map { Entry(it.getString("text"), it.getLong("at")) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "entries"
        const val KEEP_DAYS = 30
        const val MAX = 20
        private const val MAX_LENGTH = 100

        /** 검색어를 맨 앞에 더한다. 띄어쓰기와 대소문자만 다르면 같은 검색어로 본다 */
        internal fun merge(entries: List<Entry>, text: String, now: Long): List<Entry> {
            val clean = text.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty() || clean.length > MAX_LENGTH) return entries
            val key = Search.normalize(clean)
            return prune(listOf(Entry(clean, now)) + entries.filter { Search.normalize(it.text) != key }, now)
        }

        internal fun prune(entries: List<Entry>, now: Long): List<Entry> =
            entries.filter { now - it.at < KEEP_DAYS * 24 * 60 * 60 * 1000L }.take(MAX)

        @Volatile
        private var instance: SearchHistory? = null

        fun get(context: Context): SearchHistory =
            instance ?: synchronized(this) { instance ?: SearchHistory(context).also { instance = it } }
    }
}
