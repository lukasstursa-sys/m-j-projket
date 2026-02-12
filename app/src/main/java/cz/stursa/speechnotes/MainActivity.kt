package cz.stursa.speechnotes

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import cz.stursa.speechnotes.data.AppSettingsManager
import cz.stursa.speechnotes.data.BackupManager
import cz.stursa.speechnotes.data.FolderManager
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.databinding.ActivityMainBinding
import cz.stursa.speechnotes.databinding.DialogSaveNoteBinding
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer
import cz.stursa.speechnotes.speech.WhisperManager
import cz.stursa.speechnotes.widget.SpeechNotesWidget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), CzechSpeechRecognizer.SpeechResultListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var speechRecognizer: CzechSpeechRecognizer
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var backupManager: BackupManager
    private lateinit var folderManager: FolderManager
    private lateinit var settingsManager: AppSettingsManager

    private val transcribedText = StringBuilder()
    private var isRecording = false

    companion object {
        val EMOJI_LIST = listOf(
            "\u2B50", "\u2764\uFE0F", "\uD83D\uDE00", "\uD83D\uDED2",
            "\uD83D\uDE97", "\uD83D\uDEB2", "\uD83C\uDFCD\uFE0F", "\uD83C\uDF79",
            "\uD83C\uDFE0", "\uD83D\uDCBC", "\uD83C\uDF82", "\uD83C\uDF3B",
            "\uD83D\uDCB0", "\u2695\uFE0F", "\uD83C\uDF7D\uFE0F", "\uD83D\uDCC5",
            "\uD83C\uDFAF", "\uD83D\uDCA1", "\uD83D\uDD25", "\u26A0\uFE0F",
            "\u2705", "\u274C", "\uD83D\uDCDE", "\uD83C\uDFB5"
        )

        val NOTE_COLORS = listOf(
            0,
            0xFFEF5350.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(),
            0xFF42A5F5.toInt(), 0xFF26C6DA.toInt(), 0xFF66BB6A.toInt(),
            0xFFFFEE58.toInt(), 0xFFFFA726.toInt(), 0xFF8D6E63.toInt(),
            0xFFBDBDBD.toInt()
        )
    }

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
        folderManager = FolderManager(this)
        settingsManager = AppSettingsManager(this)

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
                R.id.action_search -> true
                R.id.action_sort -> { showSortDialog(); true }
                R.id.action_filter -> { showFilterDialog(); true }
                R.id.action_filter_category -> { showCategoryFilterDialog(); true }
                R.id.action_filter_folder -> { showFolderFilterDialog(); true }
                R.id.action_filter_date -> { showDateFilterDialog(); true }
                R.id.action_trash -> { showTrashDialog(); true }
                R.id.action_statistics -> { showStatisticsDialog(); true }
                R.id.action_manage_folders -> { showManageFoldersDialog(); true }
                R.id.action_display_settings -> { showDisplaySettingsDialog(); true }
                R.id.action_backup -> { performBackup(); true }
                R.id.action_restore -> { showRestoreDialog(); true }
                R.id.action_dark_mode -> { showDarkModeDialog(); true }
                R.id.action_whisper -> { showWhisperSettings(); true }
                else -> false
            }
        }

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
            if (it.length > 40) it.substring(0, 40) + "\u2026" else it
        }
        dialogBinding.editDialogTitle.setText(autoTitle)

        var selectedEmoji = ""
        val dateTimeStr = SimpleDateFormat("d.M.yyyy HH:mm", Locale("cs", "CZ")).format(Date())
        val contentWithTimestamp = "[$dateTimeStr]\n$content"

        // Setup folder picker
        dialogBinding.editDialogFolder.setOnClickListener {
            val folders = folderManager.getFolders()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.folder_hint)
                .setItems(folders.toTypedArray()) { _, which ->
                    dialogBinding.editDialogFolder.setText(folders[which])
                }
                .show()
        }

        // Setup emoji picker
        val emojiContainer = dialogBinding.emojiContainer
        EMOJI_LIST.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji
                textSize = 28f
                setPadding(12, 8, 12, 8)
                setOnClickListener {
                    selectedEmoji = emoji
                    for (i in 0 until emojiContainer.childCount) {
                        emojiContainer.getChildAt(i).alpha = 0.5f
                    }
                    this.alpha = 1.0f
                }
            }
            emojiContainer.addView(tv)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_note)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_note) { _, _ ->
                val title = dialogBinding.editDialogTitle.text.toString().ifEmpty { autoTitle }
                val label = dialogBinding.editDialogLabel.text.toString().trim()
                val category = dialogBinding.editDialogCategory.text.toString().trim()
                val folder = dialogBinding.editDialogFolder.text.toString().trim()

                val note = Note(
                    title = title,
                    content = contentWithTimestamp,
                    label = label,
                    category = category,
                    folder = folder,
                    emoji = selectedEmoji
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

    // --- Voice Commands ---

    private fun processVoiceCommand(text: String): Boolean {
        val lower = text.lowercase()
        return when {
            lower.startsWith(getString(R.string.voice_command_new)) -> {
                openNoteDetail(null)
                true
            }
            lower.startsWith(getString(R.string.voice_command_search)) -> {
                val query = lower.removePrefix(getString(R.string.voice_command_search)).trim()
                if (query.isNotEmpty()) viewModel.setSearchQuery(query)
                true
            }
            else -> false
        }
    }

    // --- Sort ---

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_by_updated),
            getString(R.string.sort_by_created),
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_folder),
            getString(R.string.sort_by_category)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_notes)
            .setItems(options) { _, which ->
                viewModel.setSortMode(which)
            }
            .show()
    }

    // --- Filters ---

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

    private fun showFolderFilterDialog() {
        val folders = folderManager.getFolders()
        val items = arrayOf(getString(R.string.all_folders)) + folders.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter_by_folder)
            .setItems(items) { _, which ->
                if (which == 0) {
                    viewModel.setFolderFilter(null)
                } else {
                    viewModel.setFolderFilter(folders[which - 1])
                }
            }
            .show()
    }

    private fun showDateFilterDialog() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day)
            }
            viewModel.setDateFilter(selected.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateFilterChips(selectedLabel: String?) {
        binding.chipGroupLabels.removeAllViews()

        val allChip = Chip(this).apply {
            text = getString(R.string.all_notes)
            isCheckable = true
            isChecked = selectedLabel == null
            setOnClickListener {
                viewModel.clearAllFilters()
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

    // --- Trash ---

    private fun showTrashDialog() {
        val deletedNotes = viewModel.deletedNotes.value
        if (deletedNotes.isNullOrEmpty()) {
            Toast.makeText(this, R.string.trash_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val items = deletedNotes.map { note ->
            "${note.emoji} ${note.title}".trim()
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trash)
            .setItems(items) { _, which ->
                val note = deletedNotes[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle(note.title)
                    .setMessage(note.content.take(200))
                    .setPositiveButton(R.string.restore_note) { _, _ ->
                        viewModel.restoreFromTrash(note.id)
                        Toast.makeText(this, "Poznamka obnovena", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.permanently_delete) { _, _ ->
                        viewModel.deleteNote(note)
                        Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton(R.string.cancel, null)
                    .show()
            }
            .setNeutralButton(R.string.empty_trash) { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setMessage("Opravdu chcete trvale smazat vsechny poznamky v kosi?")
                    .setPositiveButton(R.string.yes) { _, _ ->
                        viewModel.emptyTrash()
                        Toast.makeText(this, "Kos vysypan", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Statistics ---

    private fun showStatisticsDialog() {
        val notes = viewModel.allNotes.value ?: emptyList()
        val totalNotes = notes.size
        val totalWords = notes.sumOf { it.content.split("\\s+".toRegex()).size }
        val folders = notes.map { it.folder }.filter { it.isNotEmpty() }.distinct().size
        val categories = notes.map { it.category }.filter { it.isNotEmpty() }.distinct().size
        val labels = notes.map { it.label }.filter { it.isNotEmpty() }.distinct().size
        val pinned = notes.count { it.isPinned }
        val withReminders = notes.count { it.reminderTime > 0 }

        val msg = buildString {
            appendLine("${getString(R.string.total_notes)}: $totalNotes")
            appendLine("${getString(R.string.total_words)}: $totalWords")
            appendLine("${getString(R.string.folders_used)}: $folders")
            appendLine("Kategorii: $categories")
            appendLine("Stitku: $labels")
            appendLine("Pripnutych: $pinned")
            appendLine("S pripominkou: $withReminders")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.statistics)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // --- Manage Folders ---

    private fun showManageFoldersDialog() {
        val folders = folderManager.getFolders().toMutableList()
        val items = folders.map { "${folderManager.getFolderEmoji(it)} $it" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manage_folders)
            .setItems(items) { _, which ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(folders[which])
                    .setItems(arrayOf("Smazat slozku")) { _, _ ->
                        folderManager.removeFolder(folders[which])
                        Toast.makeText(this, "Slozka smazana", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            .setPositiveButton(R.string.add_folder) { _, _ ->
                val editText = EditText(this).apply { hint = "Nazev slozky" }
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.add_folder)
                    .setView(editText)
                    .setPositiveButton(R.string.save_note) { _, _ ->
                        val name = editText.text.toString().trim()
                        if (name.isNotEmpty()) {
                            folderManager.addFolder(name)
                            Toast.makeText(this, "Slozka pridana", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Display Settings ---

    private fun showDisplaySettingsDialog() {
        val options = arrayOf(
            getString(R.string.view_list),
            getString(R.string.view_compact)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.display_settings)
            .setItems(options) { _, which ->
                settingsManager.listViewMode = which
                Toast.makeText(this, "Zobrazeni zmeneno", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // --- Note Context Menu ---

    private fun showNoteContextMenu(note: Note) {
        val pinText = if (note.isPinned) getString(R.string.unpin_note) else getString(R.string.pin_note)
        val options = arrayOf(
            getString(R.string.edit_note),
            pinText,
            getString(R.string.share_note),
            "Kopirovat text",
            getString(R.string.set_reminder),
            getString(R.string.send_to_calendar),
            getString(R.string.send_to_chat),
            getString(R.string.move_to_trash)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openNoteDetail(note)
                    1 -> viewModel.togglePin(note)
                    2 -> shareNote(note)
                    3 -> copyNoteToClipboard(note)
                    4 -> showReminderPicker(note)
                    5 -> sendToCalendar(note)
                    6 -> sendToChat(note)
                    7 -> confirmMoveToTrash(note)
                }
            }
            .show()
    }

    // --- Reminder ---

    private fun showReminderPicker(note: Note) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val timeCal = Calendar.getInstance().apply { set(year, month, day) }
            android.app.TimePickerDialog(this, { _, hour, minute ->
                timeCal.set(Calendar.HOUR_OF_DAY, hour)
                timeCal.set(Calendar.MINUTE, minute)
                val reminderTime = timeCal.timeInMillis
                viewModel.setReminder(note.id, reminderTime)

                val reminderManager = cz.stursa.speechnotes.data.ReminderManager(this)
                reminderManager.scheduleReminder(note.id, note.title, note.content, reminderTime)

                Toast.makeText(this, R.string.reminder_set, Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // --- Google Calendar ---

    private fun sendToCalendar(note: Note) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, note.title)
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, note.content)
            if (note.reminderTime > 0) {
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, note.reminderTime)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, note.reminderTime + 3600000)
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Google Kalendar neni dostupny", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Send to Chat ---

    private fun sendToChat(note: Note) {
        val shareText = buildString {
            appendLine(note.title)
            if (note.folder.isNotEmpty()) appendLine("Slozka: ${note.folder}")
            if (note.category.isNotEmpty()) appendLine("Kategorie: ${note.category}")
            appendLine()
            append(note.content)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            setPackage("com.google.android.apps.messaging")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to any messaging app
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(fallback, getString(R.string.send_to_chat)))
        }
    }

    private fun confirmMoveToTrash(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.softDeleteNote(note)
                Toast.makeText(this, R.string.move_to_trash, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.no, null)
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
            if (note.folder.isNotEmpty()) appendLine("Slozka: ${note.folder}")
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
        Toast.makeText(this, "Text zkopirovan do schranky", Toast.LENGTH_SHORT).show()
    }

    // --- Backup ---

    private fun performBackup() {
        val result = backupManager.exportDatabase()
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private fun showRestoreDialog() {
        val backups = backupManager.listBackups()
        if (backups.isEmpty()) {
            Toast.makeText(this, "Zadne zalohy nenalezeny", Toast.LENGTH_SHORT).show()
            return
        }
        val items = backups.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_import)
            .setItems(items) { _, which ->
                MaterialAlertDialogBuilder(this)
                    .setMessage("Obnovit databazi ze zalohy ${items[which]}?\nVsechna aktualni data budou prepsana.")
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
        val modelStatus = if (models.isEmpty()) "Zadne modely" else models.joinToString(", ")

        val message = buildString {
            appendLine("Stav: $status")
            appendLine("Modely: $modelStatus")
            appendLine("API URL: ${whisperManager.apiUrl}")
            appendLine()
            appendLine("Pro offline prepis:")
            appendLine("1. Spustte whisper.cpp server lokalne")
            appendLine("2. Nebo stahnete GGML model do:")
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

        // Observe trash for badge
        viewModel.deletedNotes.observe(this) { _ -> }
    }

    // --- SpeechResultListener ---

    override fun onPartialResult(text: String) {
        runOnUiThread { binding.textLivePreview.text = text }
    }

    override fun onFinalResult(text: String) {
        runOnUiThread {
            // Check for voice commands
            if (processVoiceCommand(text)) {
                speechRecognizer.stopListening()
                isRecording = false
                updateMicButton()
                return@runOnUiThread
            }

            if (transcribedText.isNotEmpty()) transcribedText.append(" ")
            transcribedText.append(text)
            binding.textLivePreview.text = transcribedText.toString()
            if (isRecording) speechRecognizer.startListening()
        }
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            if ((errorMessage.contains("nebyla rozpoznana", ignoreCase = true) ||
                 errorMessage.contains("Nebyla detekovana", ignoreCase = true)) && isRecording
            ) {
                // Auto-restart on silence - improved continuity
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
