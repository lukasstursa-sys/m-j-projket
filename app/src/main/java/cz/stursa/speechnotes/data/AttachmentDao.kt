package cz.stursa.speechnotes.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getAttachmentsForNote(noteId: Long): LiveData<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt DESC")
    suspend fun getAttachmentsForNoteSync(noteId: Long): List<Attachment>

    @Query("SELECT COUNT(*) FROM attachments WHERE noteId = :noteId")
    suspend fun getAttachmentCount(noteId: Long): Int

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: Long)
}
