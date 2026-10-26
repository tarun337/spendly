package com.expensemanager.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val currency: String = "₹",
    /** Monthly budget in minor units, 0 = not set. */
    val monthlyBudget: Long = 0,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "DARK") }.getOrDefault(ThemeMode.DARK),
        currency = sp.getString("currency", "₹") ?: "₹",
        monthlyBudget = sp.getLong("budget", 0),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putString("currency", s.currency)
            .putLong("budget", s.monthlyBudget)
            .apply()
        _settings.value = s
    }
}
