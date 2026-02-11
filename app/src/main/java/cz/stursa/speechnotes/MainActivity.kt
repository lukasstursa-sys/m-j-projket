package cz.stursa.speechnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.stursa.speechnotes.adapter.NoteAdapter
import cz.stursa.speechnotes.data.BackupManager
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.databinding.ActivityMainBinding
import cz.stursa.speechnotes.databinding.DialogSaveNoteBinding
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer
import cz.stursa.speechnotes.speech.WhisperManager
import cz.stursa.speechnotes.widget.SpeechNotesWidget

class MainActivity : AppCompatActivity(), CzechSpeechRecognizer.SpeechResultListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var speechRecognizer: CzechSpeechRecognizer
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var backupManager: BackupManager

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
        backupManager = BackupManager(this)

        loadDarkModeSetting()
        setupToolbar()
        setupRecyclerView()
        setupMicButton()
        observeData()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when {
            intent.action == SpeechNotesWidget.ACTION_START_RECORDING -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    if (!isRecording) toggleRecording()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            intent.getStringExtra("action") == "new_note" -> {
                openNoteDetail(null)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> true // handled by SearchView
                R.id.action_filter -> { showFilterDialog(); true }
                R.id.action_filter_category -> { showCategoryFilterDialog(); true }
                R.id.action_backup -> { performBackup(); true }
                R.id.action_restore -> { showRestoreDialog(); true }
                R.id.action_dark_mode -> { showDarkModeDialog(); true }
                R.id.action_whisper -> { showWhisperSettings(); true }
                else -> false
            }
        }

        // Setup SearchView
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    viewModel.setSearchQuery(null)
                } else {
                    viewModel.setSearchQuery(newText)
                }
                return true
            }
        })
        searchView?.setOnCloseListener {
            viewModel.setSearchQuery(null)
            false
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
                val category = dialogBinding.editDialogCategory.text.toString().trim()

                val note = Note(
                    title = title,
                    content = content,
                    label = label,
                    category = category
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

    private fun showCategoryFilterDialog() {
        viewModel.allCategories.value?.let { categories ->
            val items = arrayOf(getString(R.string.all_categories)) + categories.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_by_category)
                .setItems(items) { _, which ->
                    if (which == 0) {
                        viewModel.setCategoryFilter(null)
                    } else {
                        viewModel.setCategoryFilter(categories[which - 1])
                    }
                }
                .show()
        }
    }

    private fun updateFilterChips(selectedLabel: String?) {
        binding.chipGroupLabels.removeAllViews()

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
        val pinText = if (note.isPinned) getString(R.string.unpin_note) else getString(R.string.pin_note)
        val options = arrayOf(
            getString(R.string.edit_note),
            pinText,
            getString(R.string.share_note),
            "Kopírovat text",
            getString(R.string.delete_note)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openNoteDetail(note)
                    1 -> viewModel.togglePin(note)
                    2 -> shareNote(note)
                    3 -> copyNoteToClipboard(note)
                    4 -> confirmDeleteNote(note)
                }
            }
            .show()
    }

    private fun openNoteDetail(note: Note?) {
        val intent = Intent(this, NoteDetailActivity::class.java)
        if (note != null) {
            intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
        }
        startActivity(intent)
    }

    private fun shareNote(note: Note) {
        val shareText = buildString {
            appendLine(note.title)
            if (note.label.isNotEmpty()) appendLine("[${note.label}]")
            if (note.category.isNotEmpty()) appendLine("Kategorie: ${note.category}")
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

    // --- Backup ---

    private fun performBackup() {
        val result = backupManager.exportDatabase()
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private fun showRestoreDialog() {
        val backups = backupManager.listBackups()
        if (backups.isEmpty()) {
            Toast.makeText(this, "Žádné zálohy nenalezeny", Toast.LENGTH_SHORT).show()
            return
        }
        val items = backups.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_import)
            .setItems(items) { _, which ->
                MaterialAlertDialogBuilder(this)
                    .setMessage("Obnovit databázi ze zálohy ${items[which]}?\nVšechna aktuální data budou přepsána.")
                    .setPositiveButton(R.string.yes) { _, _ ->
                        val result = backupManager.importDatabase(backups[which].absolutePath)
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
            .show()
    }

    // --- Dark Mode ---

    private fun loadDarkModeSetting() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val mode = prefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showDarkModeDialog() {
        val options = arrayOf(
            getString(R.string.dark_mode_system),
            getString(R.string.dark_mode_light),
            getString(R.string.dark_mode_dark)
        )
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val currentMode = prefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val checkedItem = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> 0
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dark_mode)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt("dark_mode", mode).apply()
                AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
            }
            .show()
    }

    // --- Whisper ---

    private fun showWhisperSettings() {
        val whisperManager = WhisperManager(this)
        val models = whisperManager.getAvailableModels()
        val status = if (whisperManager.isEnabled) "Zapnuto" else "Vypnuto"
        val modelStatus = if (models.isEmpty()) "Žádné modely" else models.joinToString(", ")

        val message = buildString {
            appendLine("Stav: $status")
            appendLine("Modely: $modelStatus")
            appendLine("API URL: ${whisperManager.apiUrl}")
            appendLine()
            appendLine("Pro offline přepis:")
            appendLine("1. Spusťte whisper.cpp server lokálně")
            appendLine("2. Nebo stáhněte GGML model do:")
            appendLine("   ${whisperManager.getModelDir().absolutePath}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.whisper_settings)
            .setMessage(message)
            .setPositiveButton(if (whisperManager.isEnabled) "Vypnout" else "Zapnout") { _, _ ->
                whisperManager.isEnabled = !whisperManager.isEnabled
                Toast.makeText(
                    this,
                    "Whisper ${if (whisperManager.isEnabled) "zapnut" else "vypnut"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Observe Data ---

    private fun observeData() {
        viewModel.filteredNotes.observe(this) { notes ->
            noteAdapter.submitList(notes)
            binding.textEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerNotes.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.allLabels.observe(this) { labels ->
            if (labels.isNotEmpty()) {
                binding.chipScrollView.visibility = View.VISIBLE
                updateFilterChips(null)
            } else {
                binding.chipScrollView.visibility = View.GONE
            }
        }
    }

    // --- SpeechResultListener ---

    override fun onPartialResult(text: String) {
        runOnUiThread { binding.textLivePreview.text = text }
    }

    override fun onFinalResult(text: String) {
        runOnUiThread {
            if (transcribedText.isNotEmpty()) transcribedText.append(" ")
            transcribedText.append(text)
            binding.textLivePreview.text = transcribedText.toString()
            if (isRecording) speechRecognizer.startListening()
        }
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            if ((errorMessage.contains("Řeč nebyla rozpoznána") ||
                 errorMessage.contains("Nebyla detekována řeč")) && isRecording
            ) {
                speechRecognizer.startListening()
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
        runOnUiThread { binding.textStatus.text = getString(R.string.listening) }
    }

    override fun onListeningStopped() {
        runOnUiThread {
            if (!isRecording) binding.textStatus.text = getString(R.string.tap_to_speak)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
