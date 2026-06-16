package com.houseofmmminq.macaco.ui.theme

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.houseofmmminq.macaco.R

/**
 * User-selectable style for the Adventures map. [styleRes] is the raw JSON Maps style to apply, or
 * null for Google's default ("Standard") map. Persisted by key via PreferencesManager.
 */
enum class MapTheme(
    val key: String,
    @StringRes val labelRes: Int,
    @RawRes val styleRes: Int?,
) {
    DARK("dark", R.string.map_theme_dark, R.raw.map_style),
    LIGHT("light", R.string.map_theme_light, R.raw.map_style_light),
    STANDARD("standard", R.string.map_theme_standard, null);

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key } ?: DARK
    }
}
