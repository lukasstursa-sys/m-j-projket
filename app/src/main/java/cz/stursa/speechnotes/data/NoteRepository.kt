package cz.stursa.speechnotes.data

import androidx.lifecycle.LiveData

class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()
    val allLabels: LiveData<List<String>> = noteDao.getAllLabels()

    fun getNotesByLabel(label: String): LiveData<List<Note>> {
        return noteDao.getNotesByLabel(label)
    }

    suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun insert(note: Note): Long {
        return noteDao.insert(note)
    }

    suspend fun update(note: Note) {
        noteDao.update(note)
    }

    suspend fun delete(note: Note) {
        noteDao.delete(note)
    }

    suspend fun deleteById(id: Long) {
        noteDao.deleteById(id)
    }
}
