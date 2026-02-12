package cz.stursa.speechnotes

import android.app.Application
import androidx.lifecycle.*
import cz.stursa.speechnotes.data.AppSettingsManager
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.data.NoteRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository = (application as SpeechNotesApp).repository

    val allNotes: LiveData<List<Note>> = repository.allNotes
    val allLabels: LiveData<List<String>> = repository.allLabels
    val allCategories: LiveData<List<String>> = repository.allCategories
    val allFolders: LiveData<List<String>> = repository.allFolders
    val deletedNotes: LiveData<List<Note>> = repository.deletedNotes
    val trashCount: LiveData<Int> = repository.trashCount
    val totalNoteCount: LiveData<Int> = repository.totalNoteCount
    val notesWithReminders: LiveData<List<Note>> = repository.notesWithReminders

    private val _selectedLabel = MutableLiveData<String?>()
    private val _selectedCategory = MutableLiveData<String?>()
    private val _selectedFolder = MutableLiveData<String?>()
    private val _searchQuery = MutableLiveData<String?>()
    private val _selectedDate = MutableLiveData<Long?>(null)
    private val _sortMode = MutableLiveData(AppSettingsManager.SORT_BY_UPDATED)
    private val _filterTrigger = MutableLiveData(0)

    val selectedFolder: LiveData<String?> = _selectedFolder
    val sortMode: LiveData<Int> = _sortMode

    val filteredNotes: LiveData<List<Note>> = _filterTrigger.switchMap {
        val query = _searchQuery.value
        val label = _selectedLabel.value
        val category = _selectedCategory.value
        val folder = _selectedFolder.value
        val date = _selectedDate.value
        val sort = _sortMode.value ?: AppSettingsManager.SORT_BY_UPDATED

        when {
            !query.isNullOrBlank() -> repository.searchNotes(query)
            date != null -> {
                val cal = Calendar.getInstance().apply { timeInMillis = date }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val endOfDay = cal.timeInMillis
                repository.getNotesByDate(startOfDay, endOfDay)
            }
            !folder.isNullOrEmpty() -> repository.getNotesByFolder(folder)
            !category.isNullOrEmpty() -> repository.getNotesByCategory(category)
            !label.isNullOrEmpty() -> repository.getNotesByLabel(label)
            else -> when (sort) {
                AppSettingsManager.SORT_BY_NAME -> repository.getAllNotesSortedByName()
                AppSettingsManager.SORT_BY_CREATED -> repository.getAllNotesSortedByCreated()
                AppSettingsManager.SORT_BY_FOLDER -> repository.getAllNotesSortedByFolder()
                AppSettingsManager.SORT_BY_CATEGORY -> repository.getAllNotesSortedByCategory()
                else -> repository.allNotes
            }
        }
    }

    fun setLabelFilter(label: String?) {
        _selectedCategory.value = null
        _selectedFolder.value = null
        _searchQuery.value = null
        _selectedDate.value = null
        _selectedLabel.value = label
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setCategoryFilter(category: String?) {
        _selectedLabel.value = null
        _selectedFolder.value = null
        _searchQuery.value = null
        _selectedDate.value = null
        _selectedCategory.value = category
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setFolderFilter(folder: String?) {
        _selectedLabel.value = null
        _selectedCategory.value = null
        _searchQuery.value = null
        _selectedDate.value = null
        _selectedFolder.value = folder
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setDateFilter(dateMillis: Long?) {
        _selectedLabel.value = null
        _selectedCategory.value = null
        _selectedFolder.value = null
        _searchQuery.value = null
        _selectedDate.value = dateMillis
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun clearAllFilters() {
        _selectedLabel.value = null
        _selectedCategory.value = null
        _selectedFolder.value = null
        _searchQuery.value = null
        _selectedDate.value = null
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    suspend fun getNoteById(id: Long): Note? = repository.getNoteById(id)

    fun insertNote(note: Note, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insert(note)
            onComplete?.invoke(id)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { repository.update(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    fun deleteNoteById(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { repository.togglePin(note.id, !note.isPinned) }
    }

    // Trash
    fun softDeleteNote(note: Note) {
        viewModelScope.launch { repository.softDelete(note.id) }
    }

    fun restoreFromTrash(noteId: Long) {
        viewModelScope.launch { repository.restoreFromTrash(noteId) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }

    // Reminders
    fun setReminder(noteId: Long, reminderTime: Long) {
        viewModelScope.launch { repository.setReminder(noteId, reminderTime) }
    }

    // Attachments
    fun getAttachmentsForNote(noteId: Long) = repository.getAttachmentsForNote(noteId)

    fun insertAttachment(attachment: cz.stursa.speechnotes.data.Attachment, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertAttachment(attachment)
            onComplete(id)
        }
    }

    fun deleteAttachment(id: Long) {
        viewModelScope.launch { repository.deleteAttachment(id) }
    }
}
