package cz.stursa.speechnotes

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import cz.stursa.speechnotes.data.AppDatabase
import cz.stursa.speechnotes.data.NoteRepository

class SpeechNotesApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val mode = prefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
