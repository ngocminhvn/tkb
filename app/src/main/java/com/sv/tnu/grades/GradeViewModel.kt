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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var hasInitializedSemester = false

    init {
        loadInitial()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun resolveCurrentSemester(semesters: List<GradeSemester>): String? {
        val target = repository.getCurrentSemesterId()
        if (target != null && semesters.any { it.hocKy == target }) {
            return target
        }
        return semesters.lastOrNull()?.hocKy
    }

    fun autoSelectCurrentSemester() {
        val transcript = when (val state = _uiState.value) {
            is GradeUiState.Success -> state.transcript
            is GradeUiState.Loading -> state.cached
            is GradeUiState.Error -> state.cached
            else -> null
        }
        if (transcript != null && transcript.semesters.isNotEmpty()) {
            _selectedSemester.value = resolveCurrentSemester(transcript.semesters)
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val cached = repository.loadCachedTranscript()
            if (cached != null && cached.semesters.isNotEmpty()) {
                _uiState.value = GradeUiState.Success(cached, isOffline = false)
                _selectedSemester.value = resolveCurrentSemester(cached.semesters)
                hasInitializedSemester = true
            } else {
                _uiState.value = GradeUiState.Loading(cached)
            }

            // Chỉ tự động tải lại từ API 1 lần mỗi phiên đăng nhập
            if (!hasSyncedInSession || cached == null) {
                _isRefreshing.value = true
                fetchLive(cached)
                _isRefreshing.value = false
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
        }
    }

    private suspend fun fetchLive(cached: GradeTranscript?) {
        val result = repository.fetchGrades()
        result.onSuccess { transcript ->
            hasSyncedInSession = true
            _uiState.value = GradeUiState.Success(transcript, isOffline = false)
            if (_selectedSemester.value == null || !hasInitializedSemester) {
                _selectedSemester.value = resolveCurrentSemester(transcript.semesters)
                hasInitializedSemester = true
            }
        }.onFailure { err ->
            val message = describeGradeError(err)
            _errorMessage.value = message
            if (cached != null) {
                _uiState.value = GradeUiState.Success(cached, isOffline = true)
            } else {
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

private fun describeGradeError(cause: Throwable): String {
    val detail = cause.message?.trim()?.take(400).orEmpty()
    return when (cause) {
        is retrofit2.HttpException -> when (cause.code()) {
            401 -> "HTTP 401: Phiên đăng nhập hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại."
            403 -> "HTTP 403: Máy chủ từ chối quyền truy cập bảng điểm."
            404 -> "HTTP 404: Không tìm thấy API bảng điểm."
            429 -> "HTTP 429: Máy chủ giới hạn yêu cầu. Hãy thử lại sau."
            in 500..599 -> "HTTP ${cause.code()}: Máy chủ TNUT đang gặp sự cố. $detail"
            else -> "HTTP ${cause.code()}: ${detail.ifBlank { "Yêu cầu bảng điểm bị từ chối." }}"
        }
        is java.net.SocketTimeoutException -> "Hết thời gian chờ kết nối hoặc phản hồi từ portal TNUT. $detail"
        is java.net.UnknownHostException -> "Không tìm thấy máy chủ portal TNUT. Kiểm tra Wi-Fi/4G. $detail"
        is java.net.ConnectException -> "Không kết nối được tới portal TNUT. $detail"
        is javax.net.ssl.SSLException -> "Kết nối bảo mật SSL/TLS tới portal không thành công. $detail"
        else -> detail.ifBlank {
            "Không thể tải bảng điểm (${cause.javaClass.simpleName}). Hãy thử lại sau."
        }
    }.trim()
}
