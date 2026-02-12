package cz.stursa.speechnotes

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.stursa.speechnotes.ai.AiSettingsManager
import cz.stursa.speechnotes.ai.AiTextProcessor
import cz.stursa.speechnotes.data.FolderManager
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.data.ReminderManager
import cz.stursa.speechnotes.databinding.ActivityNoteDetailBinding
import cz.stursa.speechnotes.markdown.MarkdownRenderer
import cz.stursa.speechnotes.service.SpeechRecordingService
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NoteDetailActivity : AppCompatActivity(), CzechSpeechRecognizer.SpeechResultListener {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }

    private lateinit var binding: ActivityNoteDetailBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var speechRecognizer: CzechSpeechRecognizer
    private lateinit var aiSettings: AiSettingsManager
    private lateinit var folderManager: FolderManager

    private var currentNote: Note? = null
    private var isRecording = false
    private var isMarkdownPreview = false
    private var hasRecordedBefore = false
    private var selectedEmoji = ""
    private var selectedColor = 0
    private var backgroundService: SpeechRecordingService? = null
    private var serviceBound = false

    private val dateTimeFormat = SimpleDateFormat("d.M.yyyy HH:mm", Locale("cs", "CZ"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) toggleRecording() else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as SpeechRecordingService.LocalBinder
            backgroundService = localBinder.getService()
            backgroundService?.serviceListener = object : SpeechRecordingService.ServiceCallback {
                override fun onTranscriptionUpdate(fullText: String, partialText: String) {
                    runOnUiThread {
                        val current = binding.editContent.text.toString()
                        if (fullText.length > current.length) {
                            binding.editContent.setText(fullText)
                            binding.editContent.setSelection(fullText.length)
                        }
                        if (partialText.isNotEmpty()) {
                            binding.textDetailStatus.text = partialText
                        }
                    }
                }
                override fun onRecordingError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@NoteDetailActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onRecordingStopped(fullText: String) {
                    runOnUiThread {
                        binding.editContent.setText(fullText)
                        binding.editContent.setSelection(fullText.length)
                        binding.textDetailStatus.text = getString(R.string.tap_to_speak)
                    }
                }
            }
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            backgroundService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speechRecognizer = CzechSpeechRecognizer(this, this)
        aiSettings = AiSettingsManager(this)
        folderManager = FolderManager(this)

        setupToolbar()
        setupMicButton()
        setupSaveButton()
        setupFolderPicker()
        setupEmojiPicker()
        setupColorPicker()
        setupResumeButton()
        setupReminderBanner()
        loadNote()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> { shareCurrentNote(); true }
                R.id.action_timestamp -> { insertTimestamp(); true }
                R.id.action_set_reminder -> { showReminderPicker(); true }
                R.id.action_send_calendar -> { sendToCalendar(); true }
                R.id.action_send_chat -> { sendToChat(); true }
                R.id.action_markdown_preview -> { toggleMarkdownPreview(); true }
                R.id.action_copy -> { copyCurrentNote(); true }
                R.id.action_background_record -> { toggleBackgroundRecording(); true }
                R.id.action_ai_summarize -> { processWithAi(AiTextProcessor.Action.SUMMARIZE); true }
                R.id.action_ai_bullet_points -> { processWithAi(AiTextProcessor.Action.BULLET_POINTS); true }
                R.id.action_ai_correct -> { processWithAi(AiTextProcessor.Action.CORRECT_GRAMMAR); true }
                R.id.action_ai_structure -> { processWithAi(AiTextProcessor.Action.STRUCTURE); true }
                R.id.action_ai_smart_rewrite -> { processWithAi(AiTextProcessor.Action.SMART_REWRITE); true }
                R.id.action_ai_translate -> { showTranslateDialog(); true }
                R.id.action_export_txt -> { exportAsTxt(); true }
                R.id.action_ai_settings -> { showAiSetupDialog(); true }
                R.id.action_delete -> { confirmDelete(); true }
                else -> false
            }
        }
    }

    private fun setupMicButton() {
        binding.btnDetailMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener { saveNote() }
    }

    private fun setupFolderPicker() {
        binding.editFolder.setOnClickListener {
            val folders = folderManager.getFolders()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.folder_hint)
                .setItems(folders.toTypedArray()) { _, which ->
                    binding.editFolder.setText(folders[which])
                }
                .show()
        }
    }

    private fun setupEmojiPicker() {
        binding.btnEmoji.setOnClickListener {
            val items = MainActivity.EMOJI_LIST.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_emoji)
                .setItems(items) { _, which ->
                    selectedEmoji = items[which]
                    binding.btnEmoji.text = selectedEmoji
                }
                .show()
        }
    }

    private fun setupColorPicker() {
        val container = binding.colorContainer
        container.removeAllViews()

        MainActivity.NOTE_COLORS.forEach { color ->
            val view = View(this).apply {
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(8, 8, 8, 8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (color == 0) {
                        setColor(Color.WHITE)
                        setStroke(2, Color.GRAY)
                    } else {
                        setColor(color)
                    }
                }
                setOnClickListener {
                    selectedColor = color
                    // Reset all borders
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        (child.background as? GradientDrawable)?.setStroke(
                            if (child == this) 4 else 0, Color.BLACK
                        )
                    }
                    (this.background as? GradientDrawable)?.setStroke(4, Color.BLACK)
                }
            }
            container.addView(view)
        }
    }

    private fun setupResumeButton() {
        binding.btnResumeRecording.setOnClickListener {
            // Resume recording - append timestamp and continue
            val timestamp = "\n[${dateTimeFormat.format(Date())}] "
            val editText = binding.editContent
            val current = editText.text.toString()
            editText.setText("$current$timestamp")
            editText.setSelection(editText.text?.length ?: 0)

            // Start recording
            if (!isRecording) {
                toggleRecording()
            }
            binding.btnResumeRecording.visibility = View.GONE
        }
    }

    private fun loadNote() {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1)
        if (noteId != -1L) {
            lifecycleScope.launch {
                currentNote = viewModel.getNoteById(noteId)
                currentNote?.let { note ->
                    binding.editTitle.setText(note.title)
                    binding.editContent.setText(note.content)
                    binding.editLabel.setText(note.label)
                    binding.editCategory.setText(note.category)
                    binding.editFolder.setText(note.folder)
                    selectedEmoji = note.emoji
                    selectedColor = note.color
                    if (note.emoji.isNotEmpty()) {
                        binding.btnEmoji.text = note.emoji
                    }
                    binding.toolbar.title = getString(R.string.edit_note)
                    updateReminderBanner()
                }
            }
        } else {
            binding.toolbar.title = getString(R.string.new_note)
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            speechRecognizer.stopListening()
            isRecording = false
            hasRecordedBefore = true
            binding.btnResumeRecording.visibility = View.VISIBLE
        } else {
            speechRecognizer.startListening()
            isRecording = true
            binding.btnResumeRecording.visibility = View.GONE
        }
        updateMicButton()
    }

    private fun updateMicButton() {
        binding.btnDetailMic.isSelected = isRecording
        binding.btnDetailMic.setImageResource(
            if (isRecording) R.drawable.ic_mic_off else R.drawable.ic_mic
        )
        binding.textDetailStatus.text = if (isRecording) {
            getString(R.string.listening)
        } else {
            getString(R.string.tap_to_speak)
        }
    }

    // --- Timestamp ---

    private fun insertTimestamp() {
        val timestamp = "\n[${dateTimeFormat.format(Date())}] "
        val editText = binding.editContent
        val start = editText.selectionStart.coerceAtLeast(0)
        val text = editText.text.toString()
        val newText = text.substring(0, start) + timestamp + text.substring(start)
        editText.setText(newText)
        editText.setSelection(start + timestamp.length)
        Toast.makeText(this, getString(R.string.insert_timestamp), Toast.LENGTH_SHORT).show()
    }

    // --- Markdown Preview ---

    private fun toggleMarkdownPreview() {
        isMarkdownPreview = !isMarkdownPreview
        if (isMarkdownPreview) {
            val content = binding.editContent.text.toString()
            binding.textMarkdownPreview.text = MarkdownRenderer.render(content)
            binding.markdownPreviewContainer.visibility = View.VISIBLE
            binding.editContainer.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
        } else {
            binding.markdownPreviewContainer.visibility = View.GONE
            binding.editContainer.visibility = View.VISIBLE
            binding.btnSave.visibility = View.VISIBLE
        }
    }

    // --- Background Recording ---

    private fun toggleBackgroundRecording() {
        if (serviceBound && backgroundService?.isRecording == true) {
            backgroundService?.stopRecording()
            stopService(Intent(this, SpeechRecordingService::class.java))
            unbindService(serviceConnection)
            serviceBound = false
            Toast.makeText(this, "Nahravani na pozadi zastaveno", Toast.LENGTH_SHORT).show()
        } else {
            val serviceIntent = Intent(this, SpeechRecordingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            Toast.makeText(this, "Nahravani na pozadi spusteno", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Reminder ---

    private fun setupReminderBanner() {
        binding.btnEditReminder.setOnClickListener { showReminderPicker() }
        binding.btnCancelReminder.setOnClickListener { cancelReminder() }
    }

    private fun updateReminderBanner() {
        val note = currentNote ?: return
        if (note.reminderTime > 0) {
            binding.reminderBanner.visibility = View.VISIBLE
            val dateStr = dateTimeFormat.format(Date(note.reminderTime))
            if (note.reminderTime > System.currentTimeMillis()) {
                binding.textReminderInfo.text = "\u23F0 Pripominka: $dateStr"
                binding.reminderBanner.setBackgroundColor(0xFFFFF3E0.toInt())
                binding.textReminderInfo.setTextColor(0xFFE65100.toInt())
            } else {
                binding.textReminderInfo.text = "\u2705 Pripominka probehla: $dateStr"
                binding.reminderBanner.setBackgroundColor(0xFFE8F5E9.toInt())
                binding.textReminderInfo.setTextColor(0xFF2E7D32.toInt())
            }
        } else {
            binding.reminderBanner.visibility = View.GONE
        }
    }

    private fun cancelReminder() {
        val note = currentNote ?: return
        viewModel.setReminder(note.id, 0)
        val reminderManager = ReminderManager(this)
        reminderManager.cancelReminder(note.id)
        currentNote = note.copy(reminderTime = 0)
        updateReminderBanner()
        Toast.makeText(this, "Pripominka zrusena", Toast.LENGTH_SHORT).show()
    }

    private fun showReminderPicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val timeCal = Calendar.getInstance().apply { set(year, month, day) }
            android.app.TimePickerDialog(this, { _, hour, minute ->
                timeCal.set(Calendar.HOUR_OF_DAY, hour)
                timeCal.set(Calendar.MINUTE, minute)
                val reminderTime = timeCal.timeInMillis

                if (currentNote != null) {
                    viewModel.setReminder(currentNote!!.id, reminderTime)
                    val reminderManager = ReminderManager(this)
                    reminderManager.scheduleReminder(
                        currentNote!!.id,
                        binding.editTitle.text.toString(),
                        binding.editContent.text.toString(),
                        reminderTime
                    )
                    currentNote = currentNote!!.copy(reminderTime = reminderTime)
                    updateReminderBanner()
                }

                Toast.makeText(this, R.string.reminder_set, Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // --- Google Calendar ---

    private fun sendToCalendar() {
        val title = binding.editTitle.text.toString()
        val content = binding.editContent.text.toString()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, content)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Google Kalendar neni dostupny", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Send to Chat ---

    private fun sendToChat() {
        val title = binding.editTitle.text.toString()
        val content = binding.editContent.text.toString()
        val shareText = "$title\n\n$content"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            setPackage("com.google.android.apps.messaging")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(fallback, getString(R.string.send_to_chat)))
        }
    }

    // --- Translate ---

    private fun showTranslateDialog() {
        val languages = arrayOf(
            "Anglictina", "Nemcina", "Francouzstina",
            "Spanelstina", "Italstina", "Polstina",
            "Slovenstina", "Rustina"
        )
        val langCodes = arrayOf(
            "anglictina", "nemcina", "francouzstina",
            "spanelstina", "italstina", "polstina",
            "slovenstina", "rustina"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.translate)
            .setItems(languages) { _, which ->
                if (!aiSettings.isConfigured()) {
                    showAiSetupDialog()
                    return@setItems
                }
                val content = binding.editContent.text.toString()
                if (content.isEmpty()) {
                    Toast.makeText(this, "Poznamka je prazdna", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                Toast.makeText(this, "Prekladam\u2026", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val processor = aiSettings.createProcessor()
                    val result = processor.translateTo(content, langCodes[which])
                    showAiResult(result)
                }
            }
            .show()
    }

    // --- Save ---

    private fun saveNote() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()
        val label = binding.editLabel.text.toString().trim()
        val category = binding.editCategory.text.toString().trim()
        val folder = binding.editFolder.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Zadejte nazev nebo text poznamky", Toast.LENGTH_SHORT).show()
            return
        }

        val finalTitle = title.ifEmpty {
            content.split(" ").take(5).joinToString(" ").let {
                if (it.length > 40) it.substring(0, 40) + "\u2026" else it
            }
        }

        // When adding content to existing note, add timestamp
        val finalContent = if (currentNote != null && content != currentNote!!.content && content.length > currentNote!!.content.length) {
            val addedPart = content.substring(currentNote!!.content.length).trimStart()
            if (addedPart.isNotEmpty() && !addedPart.startsWith("[")) {
                val timestamp = "\n[${dateTimeFormat.format(Date())}] "
                currentNote!!.content + timestamp + addedPart
            } else {
                content
            }
        } else {
            content
        }

        if (currentNote != null) {
            viewModel.updateNote(
                currentNote!!.copy(
                    title = finalTitle,
                    content = finalContent,
                    label = label,
                    category = category,
                    folder = folder,
                    emoji = selectedEmoji,
                    color = selectedColor,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            val dateTimeStr = dateTimeFormat.format(Date())
            val contentWithTimestamp = if (!finalContent.startsWith("[")) {
                "[$dateTimeStr]\n$finalContent"
            } else {
                finalContent
            }
            viewModel.insertNote(
                Note(
                    title = finalTitle,
                    content = contentWithTimestamp,
                    label = label,
                    category = category,
                    folder = folder,
                    emoji = selectedEmoji,
                    color = selectedColor
                )
            )
        }

        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun shareCurrentNote() {
        val title = binding.editTitle.text.toString()
        val content = binding.editContent.text.toString()
        val label = binding.editLabel.text.toString()
        val category = binding.editCategory.text.toString()
        val folder = binding.editFolder.text.toString()

        val shareText = buildString {
            appendLine(title)
            if (label.isNotEmpty()) appendLine("[$label]")
            if (category.isNotEmpty()) appendLine("Kategorie: $category")
            if (folder.isNotEmpty()) appendLine("Slozka: $folder")
            appendLine()
            append(content)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_note)))
    }

    private fun copyCurrentNote() {
        val content = binding.editContent.text.toString()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note", content))
        Toast.makeText(this, "Text zkopirovan do schranky", Toast.LENGTH_SHORT).show()
    }

    private fun processWithAi(action: AiTextProcessor.Action) {
        if (!aiSettings.isConfigured()) {
            showAiSetupDialog()
            return
        }
        val content = binding.editContent.text.toString()
        if (content.isEmpty()) {
            Toast.makeText(this, "Poznamka je prazdna", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Zpracovavam s AI\u2026", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = aiSettings.createProcessor().process(content, action)
            showAiResult(result)
        }
    }

    private fun showAiResult(result: AiTextProcessor.Result) {
        if (result.success) {
            MaterialAlertDialogBuilder(this@NoteDetailActivity)
                .setTitle("AI vysledek")
                .setMessage(result.text)
                .setPositiveButton("Nahradit text") { _, _ ->
                    binding.editContent.setText(result.text)
                }
                .setNeutralButton("Pripojit na konec") { _, _ ->
                    val current = binding.editContent.text.toString()
                    val timestamp = dateTimeFormat.format(Date())
                    binding.editContent.setText("$current\n\n--- AI [$timestamp] ---\n${result.text}")
                }
                .setNegativeButton("Kopirovat") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ai", result.text))
                    Toast.makeText(this, "Zkopirovano do schranky", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            Toast.makeText(this, "Chyba AI: ${result.error}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAiSetupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_ai_settings, null)
        val spinnerService = view.findViewById<android.widget.Spinner>(R.id.spinnerService)
        val editApiKey = view.findViewById<EditText>(R.id.editApiKey)
        val editApiUrl = view.findViewById<EditText>(R.id.editApiUrl)
        val editModel = view.findViewById<EditText>(R.id.editModel)

        // Service presets: name, url, model
        val services = arrayOf(
            Triple("Anthropic (Claude)", "https://api.anthropic.com/v1/messages", "claude-sonnet-4-5-20250929"),
            Triple("OpenAI (GPT)", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
            Triple("Vlastni / Ollama", "", "")
        )
        val serviceNames = services.map { it.first }.toTypedArray()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, serviceNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerService.adapter = adapter

        // Detect current service from saved URL
        val currentUrl = aiSettings.apiUrl
        val selectedIndex = when {
            currentUrl.contains("anthropic.com") -> 0
            currentUrl.contains("openai.com") -> 1
            else -> 2
        }
        spinnerService.setSelection(selectedIndex)

        editApiKey.setText(aiSettings.apiKey)
        editApiUrl.setText(aiSettings.apiUrl)
        editModel.setText(aiSettings.model)

        spinnerService.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                val (_, url, model) = services[position]
                if (url.isNotEmpty()) {
                    editApiUrl.setText(url)
                    editModel.setText(model)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nastaveni AI")
            .setView(view)
            .setPositiveButton("Ulozit") { _, _ ->
                aiSettings.apiKey = editApiKey.text.toString().trim()
                aiSettings.apiUrl = editApiUrl.text.toString().trim()
                aiSettings.model = editModel.text.toString().trim()
                Toast.makeText(this, "Nastaveni AI ulozeno", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportAsTxt() {
        val title = binding.editTitle.text.toString().ifEmpty { "poznamka" }
        val content = binding.editContent.text.toString()
        try {
            val fileName = title.replace(Regex("[^a-zA-Z0-9 ]"), "_")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.txt")
            file.writeText(buildString {
                appendLine(title)
                appendLine("=".repeat(title.length))
                appendLine()
                append(content)
            })
            Toast.makeText(this, "Exportovano do: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Chyba exportu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete() {
        if (currentNote == null) { finish(); return }
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.move_to_trash) { _, _ ->
                viewModel.softDeleteNote(currentNote!!)
                Toast.makeText(this, R.string.move_to_trash, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNeutralButton(R.string.permanently_delete) { _, _ ->
                viewModel.deleteNoteById(currentNote!!.id)
                Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // --- SpeechResultListener ---

    override fun onPartialResult(text: String) {
        runOnUiThread { binding.textDetailStatus.text = text }
    }

    override fun onFinalResult(text: String) {
        runOnUiThread {
            val current = binding.editContent.text.toString()
            val separator = if (current.isNotEmpty()) " " else ""
            binding.editContent.setText("$current$separator$text")
            binding.editContent.setSelection(binding.editContent.text?.length ?: 0)
            binding.textDetailStatus.text = getString(R.string.tap_to_speak)
            if (isRecording) speechRecognizer.startListening()

            // Check if text is long enough to offer AI structuring
            val fullText = binding.editContent.text.toString()
            val processor = AiTextProcessor("", "", "")
            if (processor.shouldOfferStructuring(fullText) && aiSettings.isConfigured()) {
                offerAiStructuring()
            }
        }
    }

    private fun offerAiStructuring() {
        MaterialAlertDialogBuilder(this)
            .setTitle("AI asistent")
            .setMessage("Text je delsi. Chcete ho nechat zpracovat AI?")
            .setPositiveButton(getString(R.string.ai_structure)) { _, _ ->
                processWithAi(AiTextProcessor.Action.STRUCTURE)
            }
            .setNeutralButton(getString(R.string.ai_smart_rewrite)) { _, _ ->
                processWithAi(AiTextProcessor.Action.SMART_REWRITE)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            if ((errorMessage.contains("nebyla rozpoznana", ignoreCase = true) ||
                 errorMessage.contains("Nebyla detekovana", ignoreCase = true)) && isRecording
            ) {
                // Auto-restart on silence - better recording continuity
                speechRecognizer.startListening()
            } else {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                isRecording = false
                hasRecordedBefore = true
                updateMicButton()
                binding.btnResumeRecording.visibility = View.VISIBLE
            }
        }
    }

    override fun onListeningStarted() {
        runOnUiThread { binding.textDetailStatus.text = getString(R.string.listening) }
    }

    override fun onListeningStopped() {
        runOnUiThread {
            if (!isRecording) binding.textDetailStatus.text = getString(R.string.tap_to_speak)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
