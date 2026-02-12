package cz.stursa.speechnotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val label: String = "",
    val category: String = "",
    val folder: String = "",
    val emoji: String = "",
    val color: Int = 0,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val reminderTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
