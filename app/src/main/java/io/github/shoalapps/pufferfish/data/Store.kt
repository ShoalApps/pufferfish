package io.github.shoalapps.pufferfish.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    /** 0 = follow the phone, 1 = light, 2 = dark */
    val theme: Int = 0,
    val dynamicColor: Boolean = true,
    /** Colour the now-playing screen from the album art. */
    val artColours: Boolean = true,
    val recentSearches: List<String> = emptyList(),
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings())
    val settings: StateFlow<Settings> = _settings

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), _settings.value)).apply()
    }

    fun rememberSearch(q: String) = updateSettings { s -> s.copy(recentSearches = (listOf(q) + s.recentSearches.filterNot { it.equals(q, true) }).take(12)) }
}
