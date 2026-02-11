package cz.stursa.speechnotes

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.stursa.speechnotes.ai.AiSettingsManager
import cz.stursa.speechnotes.ai.AiTextProcessor
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.databinding.ActivityNoteDetailBinding
import cz.stursa.speechnotes.markdown.MarkdownRenderer
import cz.stursa.speechnotes.service.SpeechRecordingService
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
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

    private var currentNote: Note? = null
    private var isRecording = false
    private var isMarkdownPreview = false
    private var backgroundService: SpeechRecordingService? = null
    private var serviceBound = false

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

        setupToolbar()
        setupMicButton()
        setupSaveButton()
        loadNote()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> { shareCurrentNote(); true }
                R.id.action_timestamp -> { insertTimestamp(); true }
                R.id.action_markdown_preview -> { toggleMarkdownPreview(); true }
                R.id.action_copy -> { copyCurrentNote(); true }
                R.id.action_background_record -> { toggleBackgroundRecording(); true }
                R.id.action_ai_summarize -> { processWithAi(AiTextProcessor.Action.SUMMARIZE); true }
                R.id.action_ai_bullet_points -> { processWithAi(AiTextProcessor.Action.BULLET_POINTS); true }
                R.id.action_ai_correct -> { processWithAi(AiTextProcessor.Action.CORRECT_GRAMMAR); true }
                R.id.action_ai_translate -> { showTranslateDialog(); true }
                R.id.action_export_txt -> { exportAsTxt(); true }
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
                    binding.toolbar.title = getString(R.string.edit_note)
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
        } else {
            speechRecognizer.startListening()
            isRecording = true
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
        val timestamp = "[${timeFormat.format(Date())}] "
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
            Toast.makeText(this, "Nahrávání na pozadí zastaveno", Toast.LENGTH_SHORT).show()
        } else {
            val serviceIntent = Intent(this, SpeechRecordingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            Toast.makeText(this, "Nahrávání na pozadí spuštěno", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Translate ---

    private fun showTranslateDialog() {
        val languages = arrayOf(
            "Angličtina", "Němčina", "Francouzština",
            "Španělština", "Italština", "Polština",
            "Slovenština", "Ruština"
        )
        val langCodes = arrayOf(
            "angličtina", "němčina", "francouzština",
            "španělština", "italština", "polština",
            "slovenština", "ruština"
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
                    Toast.makeText(this, "Poznámka je prázdná", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                Toast.makeText(this, "Překládám…", Toast.LENGTH_SHORT).show()
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

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Zadejte název nebo text poznámky", Toast.LENGTH_SHORT).show()
            return
        }

        val finalTitle = title.ifEmpty {
            content.split(" ").take(5).joinToString(" ").let {
                if (it.length > 40) it.substring(0, 40) + "…" else it
            }
        }

        if (currentNote != null) {
            viewModel.updateNote(
                currentNote!!.copy(
                    title = finalTitle,
                    content = content,
                    label = label,
                    category = category,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            viewModel.insertNote(
                Note(title = finalTitle, content = content, label = label, category = category)
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

        val shareText = buildString {
            appendLine(title)
            if (label.isNotEmpty()) appendLine("[$label]")
            if (category.isNotEmpty()) appendLine("Kategorie: $category")
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
        Toast.makeText(this, "Text zkopírován do schránky", Toast.LENGTH_SHORT).show()
    }

    private fun processWithAi(action: AiTextProcessor.Action) {
        if (!aiSettings.isConfigured()) {
            showAiSetupDialog()
            return
        }
        val content = binding.editContent.text.toString()
        if (content.isEmpty()) {
            Toast.makeText(this, "Poznámka je prázdná", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Zpracovávám s AI…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = aiSettings.createProcessor().process(content, action)
            showAiResult(result)
        }
    }

    private fun showAiResult(result: AiTextProcessor.Result) {
        if (result.success) {
            MaterialAlertDialogBuilder(this@NoteDetailActivity)
                .setTitle("AI výsledek")
                .setMessage(result.text)
                .setPositiveButton("Nahradit text") { _, _ ->
                    binding.editContent.setText(result.text)
                }
                .setNeutralButton("Připojit na konec") { _, _ ->
                    val current = binding.editContent.text.toString()
                    binding.editContent.setText("$current\n\n--- AI ---\n${result.text}")
                }
                .setNegativeButton("Kopírovat") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ai", result.text))
                    Toast.makeText(this, "Zkopírováno do schránky", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            Toast.makeText(this, "Chyba AI: ${result.error}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAiSetupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_ai_settings, null)
        val editApiKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.editApiKey
        )
        val editApiUrl = view.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.editApiUrl
        )
        val editModel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.editModel
        )

        editApiKey.setText(aiSettings.apiKey)
        editApiUrl.setText(aiSettings.apiUrl)
        editModel.setText(aiSettings.model)

        MaterialAlertDialogBuilder(this)
            .setTitle("Nastavení AI")
            .setView(view)
            .setPositiveButton("Uložit") { _, _ ->
                aiSettings.apiKey = editApiKey.text.toString().trim()
                aiSettings.apiUrl = editApiUrl.text.toString().trim()
                aiSettings.model = editModel.text.toString().trim()
                Toast.makeText(this, "Nastavení AI uloženo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportAsTxt() {
        val title = binding.editTitle.text.toString().ifEmpty { "poznamka" }
        val content = binding.editContent.text.toString()
        try {
            val fileName = title.replace(Regex("[^a-zA-Z0-9áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ ]"), "_")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.txt")
            file.writeText(buildString {
                appendLine(title)
                appendLine("=".repeat(title.length))
                appendLine()
                append(content)
            })
            Toast.makeText(this, "Exportováno do: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Chyba exportu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete() {
        if (currentNote == null) { finish(); return }
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
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
