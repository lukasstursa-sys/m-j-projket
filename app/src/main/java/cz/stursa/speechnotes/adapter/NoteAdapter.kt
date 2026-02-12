package cz.stursa.speechnotes.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private val dateFormat = SimpleDateFormat("d. M. yyyy HH:mm", Locale("cs", "CZ"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            // Emoji + Title
            val emojiPrefix = if (note.emoji.isNotEmpty()) "${note.emoji} " else ""
            binding.textNoteTitle.text = "$emojiPrefix${note.title}"
            binding.textNoteContent.text = note.content
            binding.textNoteDate.text = dateFormat.format(Date(note.updatedAt))

            // Pin indicator
            binding.textPinIndicator.visibility = if (note.isPinned) View.VISIBLE else View.GONE

            // Label chip
            if (note.label.isNotEmpty()) {
                binding.chipLabel.text = note.label
                binding.chipLabel.visibility = View.VISIBLE
            } else {
                binding.chipLabel.visibility = View.GONE
            }

            // Category chip
            if (note.category.isNotEmpty()) {
                binding.chipCategory.text = note.category
                binding.chipCategory.visibility = View.VISIBLE
            } else {
                binding.chipCategory.visibility = View.GONE
            }

            // Folder chip
            if (note.folder.isNotEmpty()) {
                binding.chipFolder.text = note.folder
                binding.chipFolder.visibility = View.VISIBLE
            } else {
                binding.chipFolder.visibility = View.GONE
            }

            // Reminder indicator
            if (note.reminderTime > 0 && note.reminderTime > System.currentTimeMillis()) {
                binding.textReminderIndicator.visibility = View.VISIBLE
                binding.textReminderIndicator.text = "\u23F0 ${dateFormat.format(Date(note.reminderTime))}"
            } else {
                binding.textReminderIndicator.visibility = View.GONE
            }

            // Note color
            if (note.color != 0) {
                try {
                    val bgColor = Color.argb(30, Color.red(note.color), Color.green(note.color), Color.blue(note.color))
                    binding.root.setCardBackgroundColor(bgColor)
                } catch (_: Exception) {
                    binding.root.setCardBackgroundColor(Color.WHITE)
                }
            } else {
                binding.root.setCardBackgroundColor(Color.WHITE)
            }

            binding.root.setOnClickListener { onNoteClick(note) }
            binding.root.setOnLongClickListener {
                onNoteLongClick(note)
                true
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
    }
}
