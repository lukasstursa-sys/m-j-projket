package cz.stursa.speechnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.stursa.speechnotes.adapter.NoteAdapter
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.databinding.ActivityMainBinding
import cz.stursa.speechnotes.databinding.DialogSaveNoteBinding
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer

class MainActivity : AppCompatActivity(), CzechSpeechRecognizer.SpeechResultListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var speechRecognizer: CzechSpeechRecognizer
    private lateinit var noteAdapter: NoteAdapter

    private val transcribedText = StringBuilder()
    private var isRecording = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                toggleRecording()
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speechRecognizer = CzechSpeechRecognizer(this, this)

        setupToolbar()
        setupRecyclerView()
        setupMicButton()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClick = { note -> openNoteDetail(note) },
            onNoteLongClick = { note -> showNoteContextMenu(note) }
        )
        binding.recyclerNotes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = noteAdapter
        }
    }

    private fun setupMicButton() {
        binding.btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            speechRecognizer.stopListening()
            isRecording = false
            updateMicButton()

            if (transcribedText.isNotEmpty()) {
                showSaveNoteDialog(transcribedText.toString())
            }
        } else {
            transcribedText.clear()
            binding.textLivePreview.text = ""
            speechRecognizer.startListening()
            isRecording = true
            updateMicButton()
        }
    }

    private fun updateMicButton() {
        binding.btnMic.isSelected = isRecording
        binding.btnMic.setImageResource(
            if (isRecording) R.drawable.ic_mic_off else R.drawable.ic_mic
        )
        binding.textStatus.text = if (isRecording) {
            getString(R.string.listening)
        } else {
            getString(R.string.tap_to_speak)
        }
    }

    private fun showSaveNoteDialog(content: String) {
        val dialogBinding = DialogSaveNoteBinding.inflate(layoutInflater)

        // Auto-generate title from first few words
        val autoTitle = content.split(" ").take(5).joinToString(" ").let {
            if (it.length > 40) it.substring(0, 40) + "…" else it
        }
        dialogBinding.editDialogTitle.setText(autoTitle)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_note)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_note) { _, _ ->
                val title = dialogBinding.editDialogTitle.text.toString().ifEmpty { autoTitle }
                val label = dialogBinding.editDialogLabel.text.toString().trim()

                val note = Note(
                    title = title,
                    content = content,
                    label = label
                )
                viewModel.insertNote(note) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFilterDialog() {
        viewModel.allLabels.value?.let { labels ->
            val items = arrayOf(getString(R.string.all_notes)) + labels.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_by_label)
                .setItems(items) { _, which ->
                    if (which == 0) {
                        viewModel.setLabelFilter(null)
                        updateFilterChips(null)
                    } else {
                        val label = labels[which - 1]
                        viewModel.setLabelFilter(label)
                        updateFilterChips(label)
                    }
                }
                .show()
        }
    }

    private fun updateFilterChips(selectedLabel: String?) {
        binding.chipGroupLabels.removeAllViews()

        // "All" chip
        val allChip = Chip(this).apply {
            text = getString(R.string.all_notes)
            isCheckable = true
            isChecked = selectedLabel == null
            setOnClickListener {
                viewModel.setLabelFilter(null)
                updateFilterChips(null)
            }
        }
        binding.chipGroupLabels.addView(allChip)

        viewModel.allLabels.value?.forEach { label ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = label == selectedLabel
                setOnClickListener {
                    viewModel.setLabelFilter(label)
                    updateFilterChips(label)
                }
            }
            binding.chipGroupLabels.addView(chip)
        }
    }

    private fun showNoteContextMenu(note: Note) {
        val options = arrayOf(
            getString(R.string.edit_note),
            getString(R.string.share_note),
            "Kopírovat text",
            getString(R.string.delete_note)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openNoteDetail(note)
                    1 -> shareNote(note)
                    2 -> copyNoteToClipboard(note)
                    3 -> confirmDeleteNote(note)
                }
            }
            .show()
    }

    private fun openNoteDetail(note: Note) {
        val intent = Intent(this, NoteDetailActivity::class.java).apply {
            putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
        }
        startActivity(intent)
    }

    private fun shareNote(note: Note) {
        val shareText = buildString {
            appendLine(note.title)
            if (note.label.isNotEmpty()) appendLine("[${note.label}]")
            appendLine()
            append(note.content)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_note)))
    }

    private fun copyNoteToClipboard(note: Note) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(note.title, note.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text zkopírován do schránky", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteNote(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.deleteNote(note)
                Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun observeData() {
        viewModel.filteredNotes.observe(this) { notes ->
            noteAdapter.submitList(notes)
            binding.textEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerNotes.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.allLabels.observe(this) { labels ->
            if (labels.isNotEmpty()) {
                binding.chipScrollView.visibility = View.VISIBLE
                updateFilterChips(viewModel.filteredNotes.value?.let { null })
            } else {
                binding.chipScrollView.visibility = View.GONE
            }
        }
    }

    // --- SpeechResultListener ---

    override fun onPartialResult(text: String) {
        runOnUiThread {
            binding.textLivePreview.text = text
        }
    }

    override fun onFinalResult(text: String) {
        runOnUiThread {
            if (transcribedText.isNotEmpty()) {
                transcribedText.append(" ")
            }
            transcribedText.append(text)
            binding.textLivePreview.text = transcribedText.toString()

            // Continue listening for more speech
            if (isRecording) {
                speechRecognizer.startListening()
            }
        }
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            if (errorMessage.contains("Řeč nebyla rozpoznána") ||
                errorMessage.contains("Nebyla detekována řeč")
            ) {
                // Auto-restart if still in recording mode
                if (isRecording) {
                    speechRecognizer.startListening()
                }
            } else {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                isRecording = false
                updateMicButton()
                if (transcribedText.isNotEmpty()) {
                    showSaveNoteDialog(transcribedText.toString())
                }
            }
        }
    }

    override fun onListeningStarted() {
        runOnUiThread {
            binding.textStatus.text = getString(R.string.listening)
        }
    }

    override fun onListeningStopped() {
        runOnUiThread {
            if (!isRecording) {
                binding.textStatus.text = getString(R.string.tap_to_speak)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
