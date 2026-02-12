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
    private val _filterTrigger = MutableLiveData(0)

    // Combined filter: search > category > label > all
    val filteredNotes: LiveData<List<Note>> = _filterTrigger.switchMap {
        val query = _searchQuery.value
        val label = _selectedLabel.value
        val category = _selectedCategory.value
        when {
            !query.isNullOrBlank() -> repository.searchNotes(query)
            !category.isNullOrEmpty() -> repository.getNotesByCategory(category)
            !label.isNullOrEmpty() -> repository.getNotesByLabel(label)
            else -> repository.allNotes
        }
    }

    fun setLabelFilter(label: String?) {
        _selectedCategory.value = null
        _searchQuery.value = null
        _selectedLabel.value = label
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setCategoryFilter(category: String?) {
        _selectedLabel.value = null
        _searchQuery.value = null
        _selectedCategory.value = category
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
        _filterTrigger.value = (_filterTrigger.value ?: 0) + 1
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
