package cz.stursa.speechnotes.data

import android.content.Context
import android.content.SharedPreferences

class FolderManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "folder_settings"
        private const val KEY_FOLDERS = "custom_folders"
        private const val KEY_FOLDER_COLORS = "folder_colors"

        val DEFAULT_FOLDERS = listOf(
            "Pracovni",
            "Doma",
            "Nakup",
            "Nakup/Albert",
            "Nakup/Lidl",
            "Nakup/Kaufland",
            "Osobni",
            "Zdravi",
            "Finance",
            "Diar",
            "Jidlo",
            "Vecere",
            "Zahrada",
            "Narozeniny",
            "Udalosti",
            "Bez slozky"
        )

        val DEFAULT_FOLDER_EMOJIS = mapOf(
            "Pracovni" to "\uD83D\uDCBC",
            "Doma" to "\uD83C\uDFE0",
            "Nakup" to "\uD83D\uDED2",
            "Nakup/Albert" to "\uD83C\uDFEA",
            "Nakup/Lidl" to "\uD83C\uDFEA",
            "Nakup/Kaufland" to "\uD83C\uDFEA",
            "Osobni" to "\uD83D\uDC64",
            "Zdravi" to "\u2695\uFE0F",
            "Finance" to "\uD83D\uDCB0",
            "Diar" to "\uD83D\uDCC5",
            "Jidlo" to "\uD83C\uDF7D\uFE0F",
            "Vecere" to "\uD83C\uDF74",
            "Zahrada" to "\uD83C\uDF3B",
            "Narozeniny" to "\uD83C\uDF82",
            "Udalosti" to "\uD83C\uDF89",
            "Bez slozky" to "\uD83D\uDCC1"
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolders(): List<String> {
        val saved = prefs.getStringSet(KEY_FOLDERS, null)
        return saved?.toList()?.sorted() ?: DEFAULT_FOLDERS
    }

    fun addFolder(folder: String) {
        val current = getFolders().toMutableSet()
        current.add(folder)
        prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
    }

    fun removeFolder(folder: String) {
        val current = getFolders().toMutableSet()
        current.remove(folder)
        prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
    }

    fun getFolderColor(folder: String): Int {
        return prefs.getInt("color_$folder", 0)
    }

    fun setFolderColor(folder: String, color: Int) {
        prefs.edit().putInt("color_$folder", color).apply()
    }

    fun getFolderEmoji(folder: String): String {
        return DEFAULT_FOLDER_EMOJIS[folder] ?: "\uD83D\uDCC1"
    }

    fun getTopLevelFolders(): List<String> {
        return getFolders().filter { !it.contains("/") }
    }

    fun getSubFolders(parent: String): List<String> {
        return getFolders().filter { it.startsWith("$parent/") }
    }
}
