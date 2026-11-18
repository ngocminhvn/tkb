package com.sv.tnu.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GradeUiState {
    data object Initial : GradeUiState
    data class Loading(val cached: GradeTranscript? = null) : GradeUiState
    data class Success(val transcript: GradeTranscript, val isOffline: Boolean = false) : GradeUiState
    data class Error(val message: String, val cached: GradeTranscript? = null) : GradeUiState
}

class GradeViewModel(
    private val repository: GradeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GradeUiState>(GradeUiState.Initial)
    val uiState: StateFlow<GradeUiState> = _uiState.asStateFlow()

    private val _selectedSemester = MutableStateFlow<String?>(null)
    val selectedSemester: StateFlow<String?> = _selectedSemester.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _expandedCourses = MutableStateFlow<Set<String>>(emptySet())
    val expandedCourses: StateFlow<Set<String>> = _expandedCourses.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val cached = repository.loadCachedTranscript()
            if (cached != null && cached.semesters.isNotEmpty()) {
                _uiState.value = GradeUiState.Success(cached, isOffline = true)
                // Select latest semester by default if available
                if (_selectedSemester.value == null) {
                    _selectedSemester.value = cached.semesters.lastOrNull()?.hocKy
                }
            } else {
                _uiState.value = GradeUiState.Loading(cached)
            }
            fetchLive(cached)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val currentCached = when (val state = _uiState.value) {
                is GradeUiState.Success -> state.transcript
                is GradeUiState.Loading -> state.cached
                is GradeUiState.Error -> state.cached
                else -> repository.loadCachedTranscript()
            }
            fetchLive(currentCached)
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchLive(cached: GradeTranscript?) {
        val result = repository.fetchGrades()
        result.onSuccess { transcript ->
            _uiState.value = GradeUiState.Success(transcript, isOffline = false)
            if (_selectedSemester.value == null && transcript.semesters.isNotEmpty()) {
                _selectedSemester.value = transcript.semesters.lastOrNull()?.hocKy
            }
        }.onFailure { err ->
            if (cached != null) {
                _uiState.value = GradeUiState.Success(cached, isOffline = true)
            } else {
                val message = err.message ?: "Không thể kết nối tới cổng portal TNUT."
                _uiState.value = GradeUiState.Error(message, cached)
            }
        }
    }

    fun selectSemester(hocKy: String?) {
        _selectedSemester.value = hocKy
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCourseExpanded(courseKey: String) {
        val current = _expandedCourses.value
        _expandedCourses.value = if (current.contains(courseKey)) {
            current - courseKey
        } else {
            current + courseKey
        }
    }
}
