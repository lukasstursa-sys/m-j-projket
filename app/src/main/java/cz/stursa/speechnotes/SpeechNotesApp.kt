package cz.stursa.speechnotes

import android.app.Application
import cz.stursa.speechnotes.data.AppDatabase
import cz.stursa.speechnotes.data.NoteRepository

class SpeechNotesApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }
}
