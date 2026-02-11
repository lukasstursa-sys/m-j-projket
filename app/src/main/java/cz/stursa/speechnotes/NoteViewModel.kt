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

    private val _selectedLabel = MutableLiveData<String?>()

    val filteredNotes: LiveData<List<Note>> = _selectedLabel.switchMap { label ->
        if (label.isNullOrEmpty()) {
            repository.allNotes
        } else {
            repository.getNotesByLabel(label)
        }
    }

    init {
        _selectedLabel.value = null
    }

    fun setLabelFilter(label: String?) {
        _selectedLabel.value = label
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
}
