package cz.stursa.speechnotes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val type: String,  // "image", "audio", "video", "file"
    val fileName: String,
    val filePath: String,
    val mimeType: String = "",
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
