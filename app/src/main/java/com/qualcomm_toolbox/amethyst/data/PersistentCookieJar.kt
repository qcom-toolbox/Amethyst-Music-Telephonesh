package com.qualcomm_toolbox.amethyst.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieJar(
    private var serverUrl: String,
    private val persistence: SessionPersistence,
) : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        loadFromDisk()
    }

    fun updateServerUrl(url: String) {
        serverUrl = ServerPreferences.normalizeServerUrl(url)
        store.clear()
        loadFromDisk()
    }

    fun clear() {
        store.clear()
        persistence.clearCookies(serverUrl)
    }

    fun hasCookies(): Boolean {
        return store.values.any { it.isNotEmpty() }
    }

    private fun loadFromDisk() {
        persistence.loadCookies(serverUrl).forEach { cookie ->
            val list = store.getOrPut(cookie.domain) { mutableListOf() }
            list.removeAll { it.name == cookie.name }
            list.add(cookie)
        }
    }

    private fun persist() {
        val all = store.values.flatten()
        persistence.saveCookies(serverUrl, all)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.filter { it.matches(url) } ?: emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
            list.add(newCookie)
        }
        persist()
    }
}
