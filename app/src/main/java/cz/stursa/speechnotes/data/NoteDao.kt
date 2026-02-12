package cz.stursa.speechnotes.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteDao {

    // Active notes (not deleted)
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND label = :label ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByLabel(label: String): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND category = :category ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByCategory(category: String): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND folder = :folder ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByFolder(folder: String): LiveData<List<Note>>

    @Query("""
        SELECT * FROM notes
        WHERE isDeleted = 0 AND (
            title LIKE '%' || :query || '%'
            OR content LIKE '%' || :query || '%'
            OR label LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR folder LIKE '%' || :query || '%'
        )
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): LiveData<List<Note>>

    // Filter by date range
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND createdAt >= :startOfDay AND createdAt < :endOfDay ORDER BY isPinned DESC, createdAt DESC")
    fun getNotesByDate(startOfDay: Long, endOfDay: Long): LiveData<List<Note>>

    // Sort variants
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, title COLLATE NOCASE ASC")
    fun getAllNotesSortedByName(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getAllNotesSortedByCreated(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, folder ASC, updatedAt DESC")
    fun getAllNotesSortedByFolder(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, category ASC, updatedAt DESC")
    fun getAllNotesSortedByCategory(): LiveData<List<Note>>

    // Trash
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): LiveData<List<Note>>

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1")
    fun getTrashCount(): LiveData<Int>

    // Reminders
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND reminderTime > 0 ORDER BY reminderTime ASC")
    fun getNotesWithReminders(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT DISTINCT label FROM notes WHERE isDeleted = 0 AND label != '' ORDER BY label")
    fun getAllLabels(): LiveData<List<String>>

    @Query("SELECT DISTINCT category FROM notes WHERE isDeleted = 0 AND category != '' ORDER BY category")
    fun getAllCategories(): LiveData<List<String>>

    @Query("SELECT DISTINCT folder FROM notes WHERE isDeleted = 0 AND folder != '' ORDER BY folder")
    fun getAllFolders(): LiveData<List<String>>

    // Statistics
    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    fun getTotalNoteCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND folder = :folder")
    suspend fun getNoteCountByFolder(folder: String): Int

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET reminderTime = :reminderTime, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReminder(id: Long, reminderTime: Long, updatedAt: Long = System.currentTimeMillis())
}
