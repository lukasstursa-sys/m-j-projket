package cz.stursa.speechnotes

import android.app.Application
import androidx.lifecycle.*
import cz.stursa.speechnotes.data.Note
import cz.stursa.speechnotes.data.NoteRepository
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository = (application as SpeechNotesApp).repository

    val allNotes: LiveData<List<Note>> = repository.allNotes
    val allLabels: LiveData<List<String>> = repository.allLabels
    val allCategories: LiveData<List<String>> = repository.allCategories

    private val _selectedLabel = MutableLiveData<String?>()
    private val _selectedCategory = MutableLiveData<String?>()
    private val _searchQuery = MutableLiveData<String?>()

    // Combined filter: search > category > label > all
    val filteredNotes: LiveData<List<Note>> = MediatorLiveData<List<Note>>().apply {
        var currentSource: LiveData<List<Note>>? = null

        fun update() {
            val query = _searchQuery.value
            val label = _selectedLabel.value
            val category = _selectedCategory.value

            val newSource = when {
                !query.isNullOrBlank() -> repository.searchNotes(query)
                !category.isNullOrEmpty() -> repository.getNotesByCategory(category)
                !label.isNullOrEmpty() -> repository.getNotesByLabel(label)
                else -> repository.allNotes
            }

            if (newSource != currentSource) {
                currentSource?.let { removeSource(it) }
                currentSource = newSource
                addSource(newSource) { value = it }
            }
        }

        addSource(_searchQuery) { update() }
        addSource(_selectedLabel) { update() }
        addSource(_selectedCategory) { update() }
    }

    init {
        _selectedLabel.value = null
        _selectedCategory.value = null
        _searchQuery.value = null
    }

    fun setLabelFilter(label: String?) {
        _selectedCategory.value = null
        _searchQuery.value = null
        _selectedLabel.value = label
    }

    fun setCategoryFilter(category: String?) {
        _selectedLabel.value = null
        _searchQuery.value = null
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    suspend fun getNoteById(id: Long): Note? {
        return repository.getNoteById(id)
    }

    fun insertNote(note: Note, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insert(note)
            onComplete?.invoke(id)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.update(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.delete(note)
        }
    }

    fun deleteNoteById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.togglePin(note.id, !note.isPinned)
        }
    }
}
