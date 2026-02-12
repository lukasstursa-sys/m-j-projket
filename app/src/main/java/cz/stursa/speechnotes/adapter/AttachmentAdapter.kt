package cz.stursa.speechnotes.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.stursa.speechnotes.data.Attachment
import cz.stursa.speechnotes.databinding.ItemAttachmentBinding

class AttachmentAdapter(
    private val onAttachmentClick: (Attachment) -> Unit,
    private val onDeleteClick: (Attachment) -> Unit
) : ListAdapter<Attachment, AttachmentAdapter.AttachmentViewHolder>(AttachmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val binding = ItemAttachmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AttachmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttachmentViewHolder(
        private val binding: ItemAttachmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(attachment: Attachment) {
            binding.textAttachmentName.text = attachment.fileName

            val (iconRes, typeLabel) = when (attachment.type) {
                "image" -> Pair(android.R.drawable.ic_menu_gallery, "Obrazek")
                "audio" -> Pair(android.R.drawable.ic_lock_silent_mode_off, "Zvuk")
                "video" -> Pair(android.R.drawable.ic_media_play, "Video")
                else -> Pair(android.R.drawable.ic_menu_save, "Soubor")
            }
            binding.iconAttachment.setImageResource(iconRes)
            binding.textAttachmentType.text = typeLabel

            binding.root.setOnClickListener { onAttachmentClick(attachment) }
            binding.btnDeleteAttachment.setOnClickListener { onDeleteClick(attachment) }
        }
    }

    class AttachmentDiffCallback : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment) = oldItem == newItem
    }
}
