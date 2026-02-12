package cz.stursa.speechnotes.data

import android.content.Context
import android.content.SharedPreferences

class AppSettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_display_settings"
        private const val KEY_LIST_VIEW_MODE = "list_view_mode"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_SORT_MODE = "sort_mode"

        const val VIEW_MODE_LIST = 0
        const val VIEW_MODE_COMPACT = 1
        const val VIEW_MODE_GRID = 2

        const val SORT_BY_UPDATED = 0
        const val SORT_BY_CREATED = 1
        const val SORT_BY_NAME = 2
        const val SORT_BY_FOLDER = 3
        const val SORT_BY_CATEGORY = 4
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var listViewMode: Int
        get() = prefs.getInt(KEY_LIST_VIEW_MODE, VIEW_MODE_LIST)
        set(value) = prefs.edit().putInt(KEY_LIST_VIEW_MODE, value).apply()

    var primaryColor: Int
        get() = prefs.getInt(KEY_PRIMARY_COLOR, 0)
        set(value) = prefs.edit().putInt(KEY_PRIMARY_COLOR, value).apply()

    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT_COLOR, 0)
        set(value) = prefs.edit().putInt(KEY_ACCENT_COLOR, value).apply()

    var sortMode: Int
        get() = prefs.getInt(KEY_SORT_MODE, SORT_BY_UPDATED)
        set(value) = prefs.edit().putInt(KEY_SORT_MODE, value).apply()
}
