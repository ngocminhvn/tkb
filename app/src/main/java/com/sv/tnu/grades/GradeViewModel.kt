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

    companion object {
        @Volatile
        var hasSyncedInSession: Boolean = false

        fun resetSessionSync() {
            hasSyncedInSession = false
        }
    }

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

    private var hasInitializedSemester = false

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val cached = repository.loadCachedTranscript()
            if (cached != null && cached.semesters.isNotEmpty()) {
                _uiState.value = GradeUiState.Success(cached, isOffline = false)
                if (!hasInitializedSemester) {
                    _selectedSemester.value = cached.semesters.lastOrNull()?.hocKy
                    hasInitializedSemester = true
                }
            } else {
                _uiState.value = GradeUiState.Loading(cached)
            }

            // Chỉ tự động tải lại từ API 1 lần mỗi phiên đăng nhập
            if (!hasSyncedInSession || cached == null) {
                _isRefreshing.value = true
                fetchLive(cached)
                _isRefreshing.value = false
                hasSyncedInSession = true
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            val currentCached = when (val state = _uiState.value) {
                is GradeUiState.Success -> state.transcript
                is GradeUiState.Loading -> state.cached
                is GradeUiState.Error -> state.cached
                else -> repository.loadCachedTranscript()
            }
            fetchLive(currentCached)
            _isRefreshing.value = false
            hasSyncedInSession = true
        }
    }

    private suspend fun fetchLive(cached: GradeTranscript?) {
        val result = repository.fetchGrades()
        result.onSuccess { transcript ->
            _uiState.value = GradeUiState.Success(transcript, isOffline = false)
            if (!hasInitializedSemester && transcript.semesters.isNotEmpty()) {
                _selectedSemester.value = transcript.semesters.lastOrNull()?.hocKy
                hasInitializedSemester = true
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
        hasInitializedSemester = true
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
