package cz.stursa.speechnotes.data

import androidx.lifecycle.LiveData

class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()
    val allLabels: LiveData<List<String>> = noteDao.getAllLabels()
    val allCategories: LiveData<List<String>> = noteDao.getAllCategories()
    val allFolders: LiveData<List<String>> = noteDao.getAllFolders()
    val deletedNotes: LiveData<List<Note>> = noteDao.getDeletedNotes()
    val trashCount: LiveData<Int> = noteDao.getTrashCount()
    val totalNoteCount: LiveData<Int> = noteDao.getTotalNoteCount()
    val notesWithReminders: LiveData<List<Note>> = noteDao.getNotesWithReminders()

    fun getNotesByLabel(label: String): LiveData<List<Note>> = noteDao.getNotesByLabel(label)
    fun getNotesByCategory(category: String): LiveData<List<Note>> = noteDao.getNotesByCategory(category)
    fun getNotesByFolder(folder: String): LiveData<List<Note>> = noteDao.getNotesByFolder(folder)
    fun getNotesByDate(startOfDay: Long, endOfDay: Long): LiveData<List<Note>> = noteDao.getNotesByDate(startOfDay, endOfDay)
    fun searchNotes(query: String): LiveData<List<Note>> = noteDao.searchNotes(query)

    // Sort variants
    fun getAllNotesSortedByName(): LiveData<List<Note>> = noteDao.getAllNotesSortedByName()
    fun getAllNotesSortedByCreated(): LiveData<List<Note>> = noteDao.getAllNotesSortedByCreated()
    fun getAllNotesSortedByFolder(): LiveData<List<Note>> = noteDao.getAllNotesSortedByFolder()
    fun getAllNotesSortedByCategory(): LiveData<List<Note>> = noteDao.getAllNotesSortedByCategory()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)
    suspend fun insert(note: Note): Long = noteDao.insert(note)
    suspend fun update(note: Note) = noteDao.update(note)
    suspend fun delete(note: Note) = noteDao.delete(note)
    suspend fun deleteById(id: Long) = noteDao.deleteById(id)
    suspend fun togglePin(id: Long, isPinned: Boolean) = noteDao.setPinned(id, isPinned)

    // Trash
    suspend fun softDelete(id: Long) = noteDao.softDelete(id)
    suspend fun restoreFromTrash(id: Long) = noteDao.restoreFromTrash(id)
    suspend fun emptyTrash() = noteDao.emptyTrash()

    // Reminders
    suspend fun setReminder(id: Long, reminderTime: Long) = noteDao.setReminder(id, reminderTime)
    suspend fun getNoteCountByFolder(folder: String): Int = noteDao.getNoteCountByFolder(folder)
}
