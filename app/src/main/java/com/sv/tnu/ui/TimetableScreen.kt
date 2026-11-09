package com.sv.tnu.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.CachedTimetable
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.ui.components.UserAvatar
import com.sv.tnu.rust.RustScheduleCore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal fun confirmedEdgeWeekDirection(distancePx: Float, thresholdPx: Float, direction: Int, hasAdjacentWeek: Boolean): Int? =
    direction.takeIf { (it == 1 || it == -1) && hasAdjacentWeek && thresholdPx > 0f && distancePx >= thresholdPx }

/** Elastic resistance, moving the real list rather than drawing a progress overlay. */
internal fun elasticEdgeOffsetPx(distancePx: Float, thresholdPx: Float, direction: Int): Float {
    if (thresholdPx <= 0f || distancePx <= 0f || (direction != -1 && direction != 1)) return 0f
    val distance = distancePx.coerceAtMost(thresholdPx * 4f)
    val stretched = 0.52f * distance / (1f + distance / (thresholdPx * 1.2f))
    return if (direction < 0) stretched else -stretched
}

/** One coherent offline screen state; never expose a partly loaded week/lesson pair. */
data class TimetableSnapshot(
    val weeks: List<WeekEntity>,
    val semesterLessons: List<LessonEntity>,
    val selectedWeek: Int?,
    val lessons: List<LessonEntity>,
)

/**
 * After a first login, Room's collectors may still hold an empty snapshot
 * when the transactional sync has already committed. Prefer the freshly read
 * SQLite rows until Room catches up; normal cached starts still use Room.
 */
/**
 * Refresh the first-time TKB composition only when real weeks have been
 * committed. Never remount on a failed/empty sync or a normal cached launch.
 */
internal fun shouldRecreateTimetableAfterFirstSync(
    hadCachedWeeksAtLaunch: Boolean,
    committedWeeks: List<WeekEntity>,
    alreadyRecreated: Boolean,
): Boolean = !hadCachedWeeksAtLaunch && committedWeeks.isNotEmpty() && !alreadyRecreated

internal fun preferJustSyncedCache(
    observedWeeks: List<WeekEntity>,
    observedLessons: List<LessonEntity>,
    justSynced: CachedTimetable?,
): CachedTimetable {
    if (justSynced != null && (
            (observedWeeks.isEmpty() && justSynced.weeks.isNotEmpty()) ||
            (observedLessons.isEmpty() && justSynced.lessons.isNotEmpty())
        )
    ) {
        return justSynced
    }
    return CachedTimetable(observedWeeks, observedLessons)
}

sealed interface TimetableSyncStatus {
    data object Loading : TimetableSyncStatus
    data class Success(val examWarning: String?) : TimetableSyncStatus
    data class Failure(val detail: String) : TimetableSyncStatus
}

class TimetableViewModel(
    private val repository: SchoolRepository,
    private val authStore: AuthStore,
) : ViewModel() {
    val hocKy: Int = authStore.hocKy.takeIf { it != 0 } ?: repository.defaultHocKy
    val avatarPath: StateFlow<String?> = repository.avatarPath
    val studentName: StateFlow<String?> = repository.studentName
    val studentId: String = authStore.username.orEmpty()

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    private val _todayYmd = MutableStateFlow(localTodayYmd())
    private val _refreshing = MutableStateFlow(false)
    // True from launch until the automatic daily sync has either started or
    // been skipped; avoids flashing "no timetable" before the first sync begins.
    private val _awaitingSync = MutableStateFlow(
        authStore.lastAutoSyncAttempt.let { last ->
            last == 0L || isVietnamDailyAutoSyncDue(System.currentTimeMillis(), last)
        },
    )
    val awaitingSync: StateFlow<Boolean> = _awaitingSync.asStateFlow()
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _syncStatus = MutableStateFlow<TimetableSyncStatus?>(null)
    val syncStatus: StateFlow<TimetableSyncStatus?> = _syncStatus.asStateFlow()

    fun dismissSyncStatus() {
        if (_syncStatus.value !is TimetableSyncStatus.Loading) _syncStatus.value = null
    }

    private val _lastSyncTime = MutableStateFlow(authStore.lastSyncTime)
    private val _justSyncedCache = MutableStateFlow<CachedTimetable?>(null)
    private val _firstLoginRenderEpoch = MutableStateFlow(0)
    val firstLoginRenderEpoch: StateFlow<Int> = _firstLoginRenderEpoch.asStateFlow()
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // A SINGLE observable snapshot is the source of truth for first render.
    // The old independent cachedWeeks -> selectedWeek -> weekLessons collectors
    // could leave Compose waiting for a delayed emission until switching tabs.
    // Room loads both cached tables offline, then we atomically choose a week
    // and its lessons. Null is reserved solely for the initial disk read.
    private fun buildSnapshot(
        weekList: List<WeekEntity>,
        lessonList: List<LessonEntity>,
        manual: Int?,
        today: String,
    ): TimetableSnapshot {
        val selected = manual?.takeIf { week -> weekList.any { it.tuanHocKy == week } }
            ?: currentWeekNumber(weekList, today)
        return TimetableSnapshot(
            weeks = weekList,
            semesterLessons = lessonList,
            selectedWeek = selected,
            lessons = if (selected == null) emptyList()
                else lessonList.filter { it.tuanHocKy == selected },
        )
    }

    // Preserve the working first-frame cache behavior. A single Room transaction
    // gives both tables from the same point in time; the blocking seed is kept
    // intentionally until a non-blocking splash handoff is implemented.
    // If the one-shot read fails, wait for the regular Room Flow rather than
    // incorrectly displaying "not synced" while offline data may still exist.
    private val initialSnapshot: TimetableSnapshot? = runCatching {
        runBlocking(Dispatchers.IO) {
            val cache = repository.cachedTimetable(hocKy)
            buildSnapshot(cache.weeks, cache.lessons, null, _todayYmd.value)
        }
    }.getOrElse { cause ->
        Log.w("TimetableViewModel", "Could not preload Room timetable; waiting for Flow", cause)
        null
    }

    val timetable: StateFlow<TimetableSnapshot?> = combine(
        repository.weeksFlow(hocKy),
        repository.semesterLessonsFlow(hocKy),
        _selectedWeek,
        _todayYmd,
        _justSyncedCache,
    ) { weekList, lessonList, manual, today, justSynced ->
        val cache = preferJustSyncedCache(weekList, lessonList, justSynced)
        buildSnapshot(cache.weeks, cache.lessons, manual, today)
    }.flowOn(Dispatchers.Default)
        // Room's replaceTimetable() is transactional. Never pin a previous
        // semester forever when the DB legitimately becomes empty.
        .map<TimetableSnapshot, TimetableSnapshot?> { it }
        .catch { cause ->
            if (cause is CancellationException) throw cause
            Log.e("TimetableViewModel", "Room timetable observation failed", cause)
            _error.value = "Không đọc được thời khóa biểu đã lưu. Hãy thử mở lại ứng dụng."
            // Preserve a successfully seeded offline schedule when observation
            // fails; only use an empty fallback when neither read worked.
            emit(initialSnapshot ?: TimetableSnapshot(emptyList(), emptyList(), null, emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSnapshot)

    // The existing settings and navigation APIs reuse that same Room snapshot.
    val weeks: StateFlow<List<WeekEntity>> = timetable
        .map { it?.weeks.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSnapshot?.weeks.orEmpty())

    val periods: StateFlow<List<PeriodEntity>> = repository.periodsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _showExamsOnHome = MutableStateFlow(authStore.showExamsOnHome)
    val showExamsOnHome: StateFlow<Boolean> = _showExamsOnHome.asStateFlow()

    /** Lịch thi hiển thị ở trang chủ: nếu tắt thì không load để tối ưu bộ nhớ & truy vấn. */
    val exams: StateFlow<List<ExamEntity>> = _showExamsOnHome
        .flatMapLatest { show ->
            if (!show) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.examsFlow(hocKy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Dùng riêng cho dialog xem toàn bộ lịch thi ở Settings. */
    val allExams: StateFlow<List<ExamEntity>> = repository.examsFlow(hocKy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessonNotes: StateFlow<Map<String, String>> = repository.lessonNotesFlow()
        .map { notes -> notes.associate { it.lessonId to it.note } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentWeekNumber: StateFlow<Int?> = combine(weeks, _todayYmd) { weekList, today ->
        currentWeekNumber(weekList, today) ?: weekList.firstOrNull()?.tuanHocKy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedWeek: StateFlow<Int?> = timetable
        .map { it?.selectedWeek }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSnapshot?.selectedWeek)

    // Settings counts the whole semester, not just whichever week is selected.
    val semesterLessons: StateFlow<List<LessonEntity>> = timetable
        .map { it?.semesterLessons.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSnapshot?.semesterLessons.orEmpty())

    private val _isFetchingExams = MutableStateFlow(false)
    val isFetchingExams: StateFlow<Boolean> = _isFetchingExams.asStateFlow()
    private val _examSyncError = MutableStateFlow<String?>(null)
    val examSyncError: StateFlow<String?> = _examSyncError.asStateFlow()

    // Clean up ONLY the exact known legacy demo-exam fixture before syncing.
    // This does not modify real portal cache, offline lessons or personal notes.
    private val cacheReadyJob = viewModelScope.launch {
        repository.prepareOfflineCache(hocKy)
    }

    init {
        // Existing installations already have a cached avatar but no name.
        // Ask the same portal profile endpoint at most once daily while the
        // name is unknown; an offline failure does not block the timetable.
        val studentId = authStore.username?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (studentId != null && !authStore.avatarPath.isNullOrBlank() &&
            authStore.studentName.isNullOrBlank() &&
            now - authStore.lastProfileCheck > 24L * 60 * 60 * 1000) {
            authStore.lastProfileCheck = now
            viewModelScope.launch { repository.fetchAndSaveAvatar(studentId) }
        }
    }

    fun setShowExamsOnHome(enable: Boolean) {
        authStore.showExamsOnHome = enable
        _showExamsOnHome.value = enable
        if (enable) {
            fetchExamsOnly(force = true)
        }
    }

    fun fetchExamsOnly(force: Boolean = false) {
        if (!authStore.showExamsOnHome && !force) return
        if (_isFetchingExams.value) return
        _isFetchingExams.value = true
        _examSyncError.value = null
        viewModelScope.launch {
            try {
                cacheReadyJob.join()
                // An explicit exam refresh must never be silently skipped.
                // If timetable sync is already fetching exams, wait until it
                // finishes, then fetch once with a visible success/error state.
                _refreshing.first { isRefreshing -> !isRefreshing }
                repository.fetchAndSaveExams(hocKy).onFailure { cause ->
                    _examSyncError.value = cause.message ?: "Không đọc được lịch thi từ portal."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _examSyncError.value = e.message ?: "Không đọc được lịch thi từ portal."
            } finally {
                _isFetchingExams.value = false
            }
        }
    }

    fun selectWeek(week: Int) {
        _selectedWeek.value = week
    }

    /** Return to the actual current week when the main TKB tab opens. */
    fun focusToday() {
        updateToday()
        _selectedWeek.value = null
    }

    /** Re-evaluate the local date after returning from the background. */
    fun updateToday() {
        val actualToday = localTodayYmd()
        if (_todayYmd.value != actualToday) {
            _todayYmd.value = actualToday
            _selectedWeek.value = null // Follow the new current week at midnight.
        }
    }

    fun moveWeek(delta: Int) {
        val list = weeks.value
        if (list.isEmpty()) return
        val current = selectedWeek.value ?: return
        val currentIndex = list.indexOfFirst { it.tuanHocKy == current }
        if (currentIndex < 0) return
        // Use actual semester week order (handles gaps and term boundaries).
        _selectedWeek.value =
            list[(currentIndex + delta).coerceIn(0, list.lastIndex)].tuanHocKy
    }

    fun refresh(showDialog: Boolean = true) {
        if (_refreshing.value) return
        _refreshing.value = true
        _syncStatus.value = if (showDialog) TimetableSyncStatus.Loading else null
        viewModelScope.launch {
            try {
                cacheReadyJob.join()
                _error.value = null
                repository.refresh(hocKy)
                    .onSuccess {
                        // Preserve the working first-login UI remount from version 319.
                        try {
                            val committed = repository.cachedTimetable(hocKy)
                            _justSyncedCache.value = committed
                            if (shouldRecreateTimetableAfterFirstSync(
                                    hadCachedWeeksAtLaunch = !initialSnapshot?.weeks.isNullOrEmpty(),
                                    committedWeeks = committed.weeks,
                                    alreadyRecreated = _firstLoginRenderEpoch.value != 0,
                                )
                            ) {
                                Log.i("TimetableViewModel",
                                    "First sync committed: weeks=${committed.weeks.size}, lessons=${committed.lessons.size}; rebuilding TKB tab")
                                _firstLoginRenderEpoch.value += 1
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("TimetableViewModel", "Could not read just-synced timetable", e)
                        }
                        _lastSyncTime.value = authStore.lastSyncTime
                        if (showDialog) _syncStatus.value = TimetableSyncStatus.Success(repository.lastSyncWarning.value)
                    }
                    .onFailure { cause ->
                        val issue = if (cause is retrofit2.HttpException && cause.code() == 401) {
                            "Phiên đăng nhập ${repository.schoolName} đã hết hạn. Vào Cài đặt → Đăng xuất và đăng nhập lại để đồng bộ."
                        } else {
                            describeTimetableSyncError(cause)
                        }
                        val warning = repository.lastSyncWarning.value
                        val detail = if (warning.isNullOrBlank()) issue else "$issue\nLịch thi: $warning"
                        if (showDialog) {
                            _error.value = "$detail Lịch đã lưu vẫn được giữ."
                            _syncStatus.value = TimetableSyncStatus.Failure(_error.value!!)
                        } else {
                            Log.w("TimetableViewModel", "Automatic timetable sync failed: $detail")
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = describeTimetableSyncError(e)
                if (showDialog) {
                    _error.value = "$detail Lịch đã lưu vẫn được giữ."
                    _syncStatus.value = TimetableSyncStatus.Failure(_error.value!!)
                } else {
                    Log.w("TimetableViewModel", "Automatic timetable sync failed: $detail", e)
                }
            } finally {
                _refreshing.value = false
                _awaitingSync.value = false
            }
        }
    }

    fun refreshIfNeeded() {
        if (_refreshing.value) return
        val now = System.currentTimeMillis()
        val lastAttempt = authStore.lastAutoSyncAttempt
        // A fresh login still downloads its FIRST timetable immediately (v319).
        // Later automatic reloads happen at most once in each Vietnam calendar
        // day, not before 06:30 Asia/Ho_Chi_Minh. Manual sync is unrestricted.
        if (lastAttempt == 0L || isVietnamDailyAutoSyncDue(now, lastAttempt)) {
            authStore.lastAutoSyncAttempt = now
            refresh(showDialog = false)
        } else {
            _awaitingSync.value = false
        }
    }

    fun nextAutoSyncDelayMillis(): Long =
        nextVietnamAutoSyncDelayMillis(
            System.currentTimeMillis(), authStore.lastAutoSyncAttempt,
        )

    fun saveLessonNote(lessonId: String, note: String) {
        viewModelScope.launch {
            repository.saveLessonNote(lessonId, note)
        }
    }

    /** Compare normalized local calendar dates, not stale week-flow timestamps. */
    private fun currentWeekNumber(weekList: List<WeekEntity>, todayYmd: String): Int? {
        if (weekList.isEmpty()) return null
        val ordered = weekList
            .map { Triple(normalizeDateToYmd(it.ngayBatDau), normalizeDateToYmd(it.ngayKetThuc), it.tuanHocKy) }
            .sortedBy { it.first }
        return ordered.firstOrNull { (start, end, _) -> todayYmd >= start && todayYmd <= end }?.third
            ?: if (todayYmd < ordered.first().first) ordered.first().third else ordered.last().third
    }
}


private fun normalizeDateToYmd(dateStr: String): String =
    RustScheduleCore.normalizeDate(dateStr)

private fun localTodayYmd(): String {
    val cal = Calendar.getInstance()
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}

private fun mondayOfLocalWeek(ymd: String): String =
    RustScheduleCore.mondayOfWeek(ymd) ?: ymd

/**
 * Swipe one CALENDAR DAY, even across a week boundary. Bounds come from
 * actual semester weeks in Room; no fabricated lessons or extra API calls.
 */
internal fun adjacentTimetableDay(
    selectedYmd: String,
    direction: Int,
    weeks: List<WeekEntity>,
): Pair<String, Int>? {
    if (direction == 0 || weeks.isEmpty()) return null
    val next = RustScheduleCore.moveCalendarDay(selectedYmd, direction) ?: return null
    val week = weeks.firstOrNull { next in
        normalizeDateToYmd(it.ngayBatDau)..normalizeDateToYmd(it.ngayKetThuc)
    } ?: return null
    return next to week.tuanHocKy
}

private fun calculateExamEndTime(gioBatDau: String, soPhut: Int): String =
    RustScheduleCore.examEndTime(gioBatDau, soPhut)

private fun timeRange(periods: Map<Int, PeriodEntity>, tietBatDau: Int, soTiet: Int): String {
    val start = periods[tietBatDau]?.gioBatDau ?: return "Tiết $tietBatDau"
    val end = periods[tietBatDau + soTiet - 1]?.gioKetThuc.orEmpty()
    return if (end.isNotBlank()) "$start – $end" else start
}

private sealed interface ScheduleItem {
    val date: String
    val sortKey: String

    data class Lesson(
        val lesson: LessonEntity,
        val timeDisplay: String,
        override val date: String,
    ) : ScheduleItem {
        override val sortKey: String = lesson.tietBatDau.toString().padStart(2, '0')
    }

    data class Exam(
        val exam: ExamEntity,
        val endTime: String,
        override val date: String,
    ) : ScheduleItem {
        override val sortKey: String = exam.gioBatDau
    }
}

/** One lazy item per card, avoiding composing every card in a whole day. */
private data class TimetableListRow(
    val date: String,
    val item: ScheduleItem?,
    val firstInDay: Boolean,
    val lastInDay: Boolean,
    val rail: Pair<String, String>?,
    val key: String,
) {
    val contentType: String = when (item) {
        null -> "empty-day"
        is ScheduleItem.Lesson -> "lesson"
        is ScheduleItem.Exam -> "exam"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val timetable by viewModel.timetable.collectAsState()
    val weeks = timetable?.weeks.orEmpty()
    val avatarPath by viewModel.avatarPath.collectAsState()
    val studentName by viewModel.studentName.collectAsState()
    val semesterLessons = timetable?.semesterLessons.orEmpty()
    val exams by viewModel.exams.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val awaitingSync by viewModel.awaitingSync.collectAsState()
    val error by viewModel.error.collectAsState()
    val lessonNotes by viewModel.lessonNotes.collectAsState()
    var noteLesson by remember { mutableStateOf<LessonEntity?>(null) }

    // The visible timeline starts at today and never exposes past lessons.
    // Re-evaluate the local date after midnight without background polling.
    var todayStr by remember { mutableStateOf(localTodayYmd()) }
    // Day selection is local UI state, initialized from the phone's calendar.
    var selectedDay by remember { mutableStateOf(todayStr) }
    // Derive the visible week from the selected day immediately. The timeline
    // itself is semester-wide and must not wait for a week-specific Room Flow.
    val weekBounds = remember(weeks) {
        weeks.map { week ->
            Triple(
                normalizeDateToYmd(week.ngayBatDau),
                normalizeDateToYmd(week.ngayKetThuc),
                week.tuanHocKy,
            )
        }
    }
    // Keep a short initial window. Even when the portal has no more weeks,
    // scrolling is allowed to request additional future calendar days.
    var loadedDayCount by remember(viewModel.hocKy, todayStr) { mutableIntStateOf(14) }
    val lastLoadedDay = remember(todayStr, loadedDayCount) {
        RustScheduleCore.moveCalendarDay(todayStr, loadedDayCount - 1) ?: todayStr
    }
    // Only explicit day taps/swipes request a programmatic scroll. A day
    // inferred from vertical scrolling must not scroll the list back.
    var requestedScrollDay by remember { mutableStateOf<String?>(null) }
    // Reopening TKB returns to today. Do not let an asynchronous week
    // emission overwrite a date explicitly picked by the user.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        selectedDay = todayStr
        viewModel.focusToday()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val actualToday = localTodayYmd()
                if (actualToday != todayStr) {
                    todayStr = actualToday
                    selectedDay = actualToday
                }
                viewModel.updateToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Re-select the real local date at midnight while the screen stays open.
    // One suspended delay until the next midnight, not repeated CPU polling.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        while (true) {
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            delay((nextMidnight - System.currentTimeMillis() + 100L).coerceAtLeast(1000L))
            val actualToday = localTodayYmd()
            if (todayStr != actualToday) {
                todayStr = actualToday
                selectedDay = actualToday
                viewModel.updateToday()
            }
        }
    }

    // The picker follows the TOP of the scrolling timeline, including dates
    // not represented by a synced WeekEntity in SQLite.
    val startDayYmd = remember(selectedDay) { mondayOfLocalWeek(selectedDay) }

    // Dots are driven by real portal data only, not a hard-coded calendar.
    val eventDays = remember(semesterLessons, exams) {
        (semesterLessons.map { normalizeDateToYmd(it.ngayHoc) } +
            exams.filterNot { it.isHoanThi }.map { normalizeDateToYmd(it.ngayThi) }).toSet()
    }

    // Reuse time labels across week navigation and lazy list recycling.
    val lessonTimes = remember(semesterLessons, periods) {
        val periodsByNumber = periods.associateBy { it.tiet }
        semesterLessons.associate { lesson ->
            lesson.idTkb to timeRange(periodsByNumber, lesson.tietBatDau, lesson.soTiet)
        }
    }
    // Process the entire semester just once per data update; the LazyColumn
    // then renders only the visible cards instead of switching weekly pages.
    val allGrouped: Map<String, List<ScheduleItem>> = remember(semesterLessons, exams, lessonTimes) {
        val lessonItems = semesterLessons.map { lesson ->
            ScheduleItem.Lesson(
                lesson = lesson,
                timeDisplay = lessonTimes[lesson.idTkb].orEmpty(),
                date = normalizeDateToYmd(lesson.ngayHoc),
            )
        }
        val examItems = exams.filterNot { it.isHoanThi }.map { exam ->
            ScheduleItem.Exam(
                exam = exam,
                endTime = calculateExamEndTime(exam.gioBatDau, exam.soPhut),
                date = normalizeDateToYmd(exam.ngayThi),
            )
        }
        (lessonItems + examItems)
            .groupBy { it.date }
            .mapValues { (_, items) -> items.sortedBy { it.sortKey } }
            .toSortedMap()
    }

    // Keep dates earlier than today inaccessible, including via horizontal swipes.
    // A far-future calendar tap extends the data window only as far as needed.
    // 0 denotes an as-yet-unsynced week, never write that as a selected week.
    fun selectDay(date: String, weekNumber: Int) {
        if (date < todayStr) return
        if (date > lastLoadedDay) {
            var cursor = lastLoadedDay
            var count = loadedDayCount
            while (cursor < date && count < 1000) {
                val next = RustScheduleCore.moveCalendarDay(cursor, 1) ?: break
                if (next <= cursor) break
                cursor = next
                count++
            }
            loadedDayCount = count + 7
        }
        selectedDay = date
        requestedScrollDay = date
        if (weekNumber > 0 && viewModel.selectedWeek.value != weekNumber) {
            viewModel.selectWeek(weekNumber)
        }
    }

    fun changeSelectedDay(direction: Int) {
        val nextDate = RustScheduleCore.moveCalendarDay(selectedDay, direction) ?: return
        if (nextDate < todayStr) return
        val week = weekBounds.firstOrNull { (start, end, _) -> nextDate in start..end }?.third ?: 0
        selectDay(nextDate, week)
    }

    // Only materialize the current window. The database may end in the
    // middle of that window; dates beyond its last known week are placeholders,
    // not a reason to stop pagination.
    val displayedGrouped: Map<String, List<ScheduleItem>> =
        remember(todayStr, loadedDayCount, allGrouped) {
            buildMap {
                var date = todayStr
                var count = 0
                while (count < loadedDayCount) {
                    put(date, allGrouped[date].orEmpty())
                    val next = RustScheduleCore.moveCalendarDay(date, 1) ?: break
                    if (next <= date) break
                    date = next
                    count++
                }
            }
        }
    // Weeks are a database synchronization boundary: an empty day within a
    // saved week is a genuine no-class day; outside it we have no schedule.
    val syncedDays = remember(displayedGrouped, weekBounds) {
        displayedGrouped.keys.filterTo(mutableSetOf()) { day ->
            weekBounds.any { (start, end, _) -> day in start..end }
        }
    }
    // Precompute day labels once for lazy composition.
    val flatRows = remember(displayedGrouped) {
        buildList {
            displayedGrouped.forEach { (date, dayItems) ->
                val rail = RustScheduleCore.dayRail(date)
                if (dayItems.isEmpty()) {
                    add(TimetableListRow(date, null, true, true, rail, "empty-$date"))
                } else {
                    dayItems.forEachIndexed { index, item ->
                        val itemKey = when (item) {
                            is ScheduleItem.Lesson -> "lesson-${item.lesson.idTkb}"
                            is ScheduleItem.Exam -> "exam-${item.exam.id}-$index"
                        }
                        add(TimetableListRow(
                            date = date,
                            item = item,
                            firstInDay = index == 0,
                            lastInDay = index == dayItems.lastIndex,
                            rail = rail,
                            key = "$date-$itemKey",
                        ))
                    }
                }
            }
        }
    }
    val firstRowByDay = remember(flatRows) {
        flatRows.withIndex().filter { it.value.firstInDay }
            .associate { it.value.date to it.index }
    }
    // Day zero is always today. Preserve scroll while new pages arrive, but
    // reset to today's boundary when the local date or semester changes.
    val listState = key(todayStr, weeks.isNotEmpty(), viewModel.hocKy) {
        rememberLazyListState(initialFirstVisibleItemIndex = 0)
    }
    // Prefetch more days whenever the list approaches its current end,
    // including AFTER the final week saved in SQLite. The next page is
    // appended only on demand; the list state is stable across appends.
    androidx.compose.runtime.LaunchedEffect(listState, flatRows) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisible ->
            val lastLoaded = flatRows.lastOrNull()?.date
            if (lastLoaded != null &&
                lastVisible >= (flatRows.size - 6).coerceAtLeast(0) &&
                RustScheduleCore.moveCalendarDay(lastLoaded, 1) != null
            ) {
                loadedDayCount += 7
            }
        }
    }

    // The FIRST visible card touching the viewport's top edge controls the
    // highlighted date. No waiting for gesture completion or switching pages.
    // Do not force-select the final date when it is merely visible at bottom.
    androidx.compose.runtime.LaunchedEffect(listState, flatRows, weekBounds) {
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.firstOrNull { item ->
                item.offset + item.size > layout.viewportStartOffset
            }?.index ?: listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { index ->
            if (requestedScrollDay != null) return@collect
            val visibleDay = flatRows.getOrNull(index)?.date ?: return@collect
            if (visibleDay != selectedDay) {
                selectedDay = visibleDay
                weekBounds.firstOrNull { (start, end, _) -> visibleDay in start..end }
                    ?.third?.let { week ->
                        if (viewModel.selectedWeek.value != week) viewModel.selectWeek(week)
                    }
            }
        }
    }

    // Picker taps navigate to their loaded date, including newly requested
    // future pages. Large jumps skip costly off-screen scroll animation.
    androidx.compose.runtime.LaunchedEffect(requestedScrollDay, firstRowByDay, listState) {
        val requested = requestedScrollDay ?: return@LaunchedEffect
        val target = firstRowByDay[requested] ?: return@LaunchedEffect
        val firstVisible = listState.firstVisibleItemIndex
        if (firstVisible != target) {
            if (listState.isScrollInProgress || kotlin.math.abs(target - firstVisible) > 4) {
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
        if (requestedScrollDay == requested) requestedScrollDay = null
    }

    // The horizontal swipe detector must not restart whenever scrolling
    // selects another day, or fast continuous swipes can be interrupted.
    val currentSwipeDay = rememberUpdatedState<(Int) -> Unit> { direction ->
        changeSelectedDay(direction)
    }

    Scaffold(
        topBar = {
            // Student header follows the portal's avatar/name/ID layout.
            // Use the app's blue primary instead of introducing teal accents.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    avatarPath = avatarPath,
                    defaultIcon = Icons.Default.Person,
                    modifier = Modifier
                        .size(42.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape),
                    iconSize = 22.dp,
                    contentDescription = "Ảnh đại diện sinh viên",
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (!studentName.isNullOrBlank()) {
                        Text(
                            text = studentName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = viewModel.studentId.ifBlank { "Chưa có MSSV" }.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        maxLines = 1,
                    )
                    Text(
                        text = "Học kỳ ${viewModel.hocKy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = !refreshing,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Đồng bộ thời khóa biểu",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Weekly calendar replaces the old "Tuần 10" capsule.
            // Swipe horizontally for nearby weeks, down to show the month.
            WeekDatePicker(
                weekStartYmd = startDayYmd,
                selectedYmd = selectedDay,
                todayYmd = todayStr,
                eventDays = eventDays,
                availableWeeks = weeks,
                onPickDate = { date, weekNumber -> selectDay(date, weekNumber) },
                onMoveDay = { direction -> changeSelectedDay(direction) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (timetable == null || (weeks.isEmpty() && (refreshing || awaitingSync))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (displayedGrouped.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            error?.startsWith("Không đọc được thời khóa biểu đã lưu") == true ->
                                "Không đọc được lịch đã lưu."
                            weeks.isEmpty() && semesterLessons.isEmpty() ->
                                "Chưa có thời khóa biểu được đồng bộ."
                            else -> "Không tìm thấy tuần học trong lịch đã lưu."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            } else if (displayedGrouped.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { change, drag ->
                                        totalDrag += drag
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDrag) > 64.dp.toPx()) {
                                            currentSwipeDay.value(if (totalDrag < 0f) 1 else -1)
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                )
                            },
                        // The timeline continues across week boundaries without a pager.
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    items(
                        items = flatRows,
                        key = { it.key },
                        contentType = { it.contentType },
                    ) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (row.firstInDay) 8.dp else 0.dp,
                                    bottom = if (row.lastInDay) 8.dp else 0.dp,
                                ),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (row.firstInDay) {
                                DayRail(rail = row.rail, isToday = row.date == todayStr)
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                when (val item = row.item) {
                                    null -> Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(76.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        ),
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = if (row.date in syncedDays)
                                                    "Không có lịch học hoặc lịch thi."
                                                else "Chưa đồng bộ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                    is ScheduleItem.Lesson -> LessonCard(
                                        lesson = item.lesson,
                                        time = item.timeDisplay,
                                        isToday = row.date == todayStr,
                                        note = lessonNotes[item.lesson.idTkb].orEmpty(),
                                        onEditNote = { noteLesson = item.lesson },
                                    )
                                    is ScheduleItem.Exam -> ExamCard(
                                        exam = item.exam,
                                        endTime = item.endTime,
                                        isToday = row.date == todayStr,
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    noteLesson?.let { lesson ->
        LessonNoteDialog(
            lesson = lesson,
            initialNote = lessonNotes[lesson.idTkb].orEmpty(),
            onDismiss = { noteLesson = null },
            onSave = { note ->
                viewModel.saveLessonNote(lesson.idTkb, note)
                noteLesson = null
            },
        )
    }
}

@Composable
private fun DayRail(rail: Pair<String, String>?, isToday: Boolean) {
    // Day labels are cached with the week's flattened rows.
    val weekday = rail?.second.orEmpty()
    Column(
        modifier = Modifier.width(48.dp).padding(top = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = rail?.first.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = weekday,
            style = MaterialTheme.typography.bodySmall,
            color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Measure against the real space left after the pencil/exam badge.
 * Fit the subject on ONE line without changing the font for short names.
 * Very long names fall back to an ellipsis once the legible minimum is hit.
 */
@Composable
private fun AutoFitSubjectTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    // Shrink through the real text layout instead of BoxWithConstraints
    // subcomposition plus repeated TextMeasurer passes while the list scrolls.
    var fontSize by remember(title, baseStyle.fontSize) { mutableStateOf(baseStyle.fontSize) }
    var fitted by remember(title, baseStyle.fontSize) { mutableStateOf(false) }
    val atMinimum = fontSize.value <= 10f
    Text(
        text = title,
        style = baseStyle.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        overflow = if (atMinimum) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { layout ->
            if (!fitted) {
                if (layout.didOverflowWidth && !atMinimum) {
                    fontSize = (fontSize.value - 0.5f).coerceAtLeast(10f).sp
                } else {
                    fitted = true
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent { if (fitted) drawContent() },
    )
}

@Composable
private fun LessonCard(
    lesson: LessonEntity,
    time: String,
    isToday: Boolean,
    note: String,
    onEditNote: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // The pencil replaces the old credits badge. The note editor
            // opens only when the student taps it; the original icon-button
            // style is retained (no newly added Liquid Glass buttons).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AutoFitSubjectTitle(
                    title = lesson.tenMon,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onEditNote,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (note.isBlank()) "Thêm ghi chú" else "Sửa ghi chú",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Time & Period pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• T${lesson.tietBatDau} · ${lesson.soTiet} tiết",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Details info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Phòng: ${lesson.maPhong.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GV: ${lesson.tenGiangVien.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${lesson.maMon} • Nhóm ${lesson.maNhom}${if (lesson.tenLop.isNotBlank()) " • Lớp " + lesson.tenLop else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            // Only saved notes occupy a row; empty cards show the pencil only.
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable(onClick = onEditNote)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonNoteDialog(
    lesson: LessonEntity,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember(lesson.idTkb, initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi chú buổi học") },
        text = {
            Column {
                Text(
                    text = lesson.tenMon,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { value ->
                        if (value.length <= 500) note = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nội dung") },
                    placeholder = { Text("Ví dụ: Mang laptop, nộp bài tập…") },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(if (note.isBlank()) "Để trống để xoá ghi chú" else "${note.length}/500")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.trim()) }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Huỷ")
            }
        },
    )
}

@Composable
private fun ExamCard(exam: ExamEntity, endTime: String, isToday: Boolean) {
    val examRed = Color(0xFFE53935)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isToday) BorderStroke(1.5.dp, examRed) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tên môn & Huy hiệu "THI" màu đỏ thay cho phần tín chỉ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AutoFitSubjectTitle(
                    title = exam.tenMon,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Thay phần "1 TC" bằng màu đỏ "THI"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(examRed.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "THI",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = examRed,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Giờ thi & thời lượng pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(examRed.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = examRed,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${exam.gioBatDau}${if (endTime.isNotBlank()) " – $endTime" else ""}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = examRed,
                    ),
                )
                if (exam.soPhut > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${exam.soPhut} phút",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                if (exam.isGiuaKy) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Giữa kỳ",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phòng thi & Địa điểm
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Phòng: ${exam.maPhong.ifBlank { "—" }}${if (exam.diaDiemThi.isNotBlank()) " • " + exam.diaDiemThi else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // SBD & Hình thức thi
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SBD: ${exam.soBaoDanh.ifBlank { "—" }}${if (exam.hinhThucThi.isNotBlank()) " • Hình thức: " + exam.hinhThucThi else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (exam.dotThi.isNotBlank() || exam.ghiChuSv.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${exam.maMon}${if (exam.dotThi.isNotBlank()) " • Đợt " + exam.dotThi else ""}${if (exam.ghiChuSv.isNotBlank()) " • " + exam.ghiChuSv else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}
