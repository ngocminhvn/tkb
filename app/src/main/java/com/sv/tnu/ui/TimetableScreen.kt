package com.sv.tnu.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.ui.components.UserAvatar
import com.sv.tnu.rust.RustScheduleCore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** One coherent offline screen state; never expose a partly loaded week/lesson pair. */
data class TimetableSnapshot(
    val weeks: List<WeekEntity>,
    val semesterLessons: List<LessonEntity>,
    val selectedWeek: Int?,
    val lessons: List<LessonEntity>,
)

class TimetableViewModel(
    private val repository: TnutRepository,
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
            last == 0L || !isSameLocalDay(last, System.currentTimeMillis())
        },
    )
    val awaitingSync: StateFlow<Boolean> = _awaitingSync.asStateFlow()
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(authStore.lastSyncTime)
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
    ) { weekList, lessonList, manual, today ->
        buildSnapshot(weekList, lessonList, manual, today)
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

    private val _visibleWeekCount = MutableStateFlow(authStore.visibleWeekCount)
    val visibleWeekCount: StateFlow<Int> = _visibleWeekCount.asStateFlow()

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

    /**
     * Tùy chọn hiển thị số tuần:
     * Ví dụ 4 tuần: (4-1)/2 = 1 tuần trước, 1 tuần hiện tại, 2 tuần sau.
     * Ví dụ 5 tuần: (5-1)/2 = 2 tuần trước, 1 tuần hiện tại, 2 tuần sau.
     */
    val visibleWeeks: StateFlow<List<WeekEntity>> =
        combine(weeks, _todayYmd, _visibleWeekCount, _selectedWeek) { weekList, today, count, manual ->
            if (weekList.isEmpty()) emptyList()
            else {
                // Preserve the number-of-weeks setting while allowing month
                // navigation to any available semester week. Recenter the
                // window on an explicitly selected week.
                // Calculate the current week from the same Room emission rather
                // than waiting for a separate StateFlow with an initial null.
                val center = manual ?: currentWeekNumber(weekList, today) ?: 1
                val n = count.coerceIn(1, 20)
                val weeksBefore = (n - 1) / 2
                val weeksAfter = n - 1 - weeksBefore
                weekList.filter { it.tuanHocKy in (center - weeksBefore)..(center + weeksAfter) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setVisibleWeekCount(count: Int) {
        val clamped = count.coerceIn(1, 20)
        authStore.visibleWeekCount = clamped
        _visibleWeekCount.value = clamped
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

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                cacheReadyJob.join()
                _error.value = null
                repository.refresh(hocKy)
                    .onSuccess {
                        _lastSyncTime.value = authStore.lastSyncTime
                    }
                    .onFailure { cause ->
                        _error.value = if (cause is retrofit2.HttpException && cause.code() == 401) {
                            "Phiên đăng nhập TNUT đã hết hạn. Vào Cài đặt → Đăng xuất và đăng nhập lại để đồng bộ. Lịch đã lưu vẫn được giữ."
                        } else {
                            "Không tải được lịch mới: ${cause.message ?: "lỗi mạng"}. Đang hiện lịch đã lưu."
                        }
                    }
            } finally {
                _refreshing.value = false
                _awaitingSync.value = false
            }
        }
    }

    fun refreshIfNeeded() {
        val now = System.currentTimeMillis()
        // saveLogin() resets the automatic attempt marker, so signing in
        // always fetches both timetable and exams, regardless of earlier cache.
        // Within an existing session, retain the once-per-local-day policy.
        val lastAutomaticCheck = authStore.lastAutoSyncAttempt
        if (lastAutomaticCheck == 0L || !isSameLocalDay(lastAutomaticCheck, now)) {
            authStore.lastAutoSyncAttempt = now
            refresh()
        } else {
            _awaitingSync.value = false
        }
    }

    private fun isSameLocalDay(first: Long, second: Long): Boolean {
        val firstDay = Calendar.getInstance().apply { timeInMillis = first }
        val secondDay = Calendar.getInstance().apply { timeInMillis = second }
        return firstDay.get(Calendar.ERA) == secondDay.get(Calendar.ERA) &&
            firstDay.get(Calendar.YEAR) == secondDay.get(Calendar.YEAR) &&
            firstDay.get(Calendar.DAY_OF_YEAR) == secondDay.get(Calendar.DAY_OF_YEAR)
    }

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

private val dayFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
private val dayLabelFormat = ThreadLocal.withInitial { SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")) }

private fun normalizeDateToYmd(dateStr: String): String =
    RustScheduleCore.normalizeDate(dateStr)

private fun localTodayYmd(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private fun mondayOfLocalWeek(ymd: String): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val date = runCatching { format.parse(ymd) }.getOrNull() ?: return ymd
    val calendar = Calendar.getInstance().apply {
        time = date
        val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_MONTH, -offset)
    }
    return format.format(calendar.time)
}

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

private fun dayLabel(ngayHoc: String): String {
    val datePart = normalizeDateToYmd(ngayHoc)
    val date = runCatching { dayFormat.get().parse(datePart) }.getOrNull() ?: return datePart
    return dayLabelFormat.get().format(date).replaceFirstChar { it.uppercase() }
}

private fun calculateExamEndTime(gioBatDau: String, soPhut: Int): String =
    RustScheduleCore.examEndTime(gioBatDau, soPhut)

private fun timeRange(periods: List<PeriodEntity>, tietBatDau: Int, soTiet: Int): String {
    val start = periods.firstOrNull { it.tiet == tietBatDau }?.gioBatDau ?: return "Tiết $tietBatDau"
    val endTiet = tietBatDau + soTiet - 1
    val end = periods.firstOrNull { it.tiet == endTiet }?.gioKetThuc ?: ""
    return if (end.isNotBlank()) "$start – $end" else start
}

private sealed interface ScheduleItem {
    val date: String
    val sortKey: String

    data class Lesson(
        val lesson: LessonEntity,
        val timeDisplay: String,
    ) : ScheduleItem {
        override val date: String get() = normalizeDateToYmd(lesson.ngayHoc)
        override val sortKey: String get() = lesson.tietBatDau.toString().padStart(2, '0')
    }

    data class Exam(
        val exam: ExamEntity,
        val endTime: String,
    ) : ScheduleItem {
        override val date: String get() = normalizeDateToYmd(exam.ngayThi)
        override val sortKey: String get() = exam.gioBatDau
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
    val selectedWeek = timetable?.selectedWeek
    val lessons = timetable?.lessons.orEmpty()
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

    // Both lessons and exams share one past-day filter. Re-evaluate the local
    // date when the app resumes after midnight, without background polling.
    var todayStr by remember { mutableStateOf(localTodayYmd()) }
    // Day selection is local UI state, initialized from the phone's calendar.
    var selectedDay by remember { mutableStateOf(todayStr) }
    var scrollRequest by remember { mutableIntStateOf(0) }
    var hasAutoScrolled by remember { mutableStateOf(false) }
    // Reopening TKB always returns to today instead of a stale selected week.
    androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.focusToday() }
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

    val weekInfo = weeks.firstOrNull { it.tuanHocKy == selectedWeek }
    val startDayYmd = remember(weekInfo) {
        weekInfo?.let { normalizeDateToYmd(it.ngayBatDau) } ?: ""
    }
    val endDayYmd = remember(weekInfo) {
        weekInfo?.let { normalizeDateToYmd(it.ngayKetThuc) } ?: ""
    }
    // Sync the highlighted day with navigation: today in the current week,
    // otherwise the first day of the manually chosen week. Keep explicit taps.
    androidx.compose.runtime.LaunchedEffect(weekInfo?.tuanHocKy, todayStr) {
        if (startDayYmd.isNotBlank() && selectedDay !in startDayYmd..endDayYmd) {
            selectedDay = if (todayStr in startDayYmd..endDayYmd) todayStr else startDayYmd
        }
    }

    // Dots are driven by real portal data only, not a hard-coded calendar.
    val eventDays = remember(semesterLessons, exams) {
        (semesterLessons.map { normalizeDateToYmd(it.ngayHoc) } +
            exams.filterNot { it.isHoanThi }.map { normalizeDateToYmd(it.ngayThi) }).toSet()
    }

    val examsInWeek = remember(exams, startDayYmd, endDayYmd) {
        if (startDayYmd.isNotBlank() && endDayYmd.isNotBlank()) {
            exams.filter { exam ->
                if (exam.isHoanThi) return@filter false
                val examYmd = normalizeDateToYmd(exam.ngayThi)
                examYmd in startDayYmd..endDayYmd
            }
        } else {
            emptyList()
        }
    }

    // Gộp cả tiết học và buổi thi trong tuần, sắp xếp theo giờ
    val allGrouped: Map<String, List<ScheduleItem>> = remember(lessons, examsInWeek, periods) {
        val lessonItems = lessons.map { lesson ->
            ScheduleItem.Lesson(
                lesson = lesson,
                timeDisplay = timeRange(periods, lesson.tietBatDau, lesson.soTiet),
            )
        }
        val examItems = examsInWeek.map { exam ->
            ScheduleItem.Exam(
                exam = exam,
                endTime = calculateExamEndTime(exam.gioBatDau, exam.soPhut),
            )
        }
        (lessonItems + examItems)
            .groupBy { it.date }
            .mapValues { (_, items) -> items.sortedBy { it.sortKey } }
            .toSortedMap()
    }

    // Render the whole seven-day week. The selected day controls scroll
    // position, not which classes are hidden. This also makes empty dates
    // selectable and visible without losing adjacent lessons.
    fun changeSelectedDay(direction: Int) {
        val next = adjacentTimetableDay(selectedDay, direction, weeks) ?: return
        selectedDay = next.first
        scrollRequest++
        if (selectedWeek != next.second) viewModel.selectWeek(next.second)
    }

    val displayedGrouped: Map<String, List<ScheduleItem>> = remember(startDayYmd, allGrouped) {
        if (startDayYmd.isBlank()) emptyMap()
        else weekDates(startDayYmd).associateWith { date -> allGrouped[date].orEmpty() }
    }
    // A new week must paint at the selected/current day on its first frame;
    // scrolling only after composition briefly exposes earlier lessons.
    val initialDayIndex = displayedGrouped.keys.indexOf(selectedDay).takeIf { it >= 0 }
        ?: displayedGrouped.keys.indexOf(todayStr).takeIf { it >= 0 }
        ?: 0
    val listState = key(startDayYmd) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialDayIndex)
    }
    androidx.compose.runtime.LaunchedEffect(selectedDay, scrollRequest, startDayYmd, listState) {
        if (selectedDay in displayedGrouped) {
            // Each calendar day is one row: left date rail and right cards.
            val target = displayedGrouped.keys.indexOf(selectedDay)
            if (!hasAutoScrolled) {
                listState.scrollToItem(target)
                hasAutoScrolled = true
            } else {
                val firstVisible = listState.firstVisibleItemIndex
                if (kotlin.math.abs(target - firstVisible) > 5) {
                    val nearTarget = if (target < firstVisible) target + 3
                        else (target - 3).coerceAtLeast(0)
                    listState.scrollToItem(nearTarget)
                }
                listState.animateScrollToItem(target)
            }
        }
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
                weekStartYmd = startDayYmd.ifBlank { mondayOfLocalWeek(todayStr) },
                selectedYmd = selectedDay,
                todayYmd = todayStr,
                eventDays = eventDays,
                availableWeeks = weeks,
                onPickDate = { date, weekNumber ->
                    selectedDay = date
                    scrollRequest++
                    if (selectedWeek != weekNumber) viewModel.selectWeek(weekNumber)
                },
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
                            .pointerInput(selectedDay, weeks, selectedWeek) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { change, drag ->
                                        totalDrag += drag
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDrag) > 64.dp.toPx()) {
                                            changeSelectedDay(if (totalDrag < 0f) 1 else -1)
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                )
                            },
                        // Leave a viewport of scroll space so Saturday/Sunday
                        // can also be brought to the top when tapped.
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + maxHeight,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    displayedGrouped.forEach { (date, dayItems) ->
                        item(key = "day-$date", contentType = "day") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                DayRail(date = date, isToday = date == todayStr)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (dayItems.isEmpty()) {
                                        Card(
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
                                                    text = "Không có lịch học hoặc lịch thi.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    } else {
                                        dayItems.forEach { item ->
                                            when (item) {
                                                is ScheduleItem.Lesson -> LessonCard(
                                                    lesson = item.lesson,
                                                    time = item.timeDisplay,
                                                    isToday = date == todayStr,
                                                    note = lessonNotes[item.lesson.idTkb].orEmpty(),
                                                    onEditNote = { noteLesson = item.lesson },
                                                )
                                                is ScheduleItem.Exam -> ExamCard(
                                                    exam = item.exam,
                                                    endTime = item.endTime,
                                                    isToday = date == todayStr,
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
private fun DayRail(date: String, isToday: Boolean) {
    // Rust computes the label; no SimpleDateFormat is built while scrolling.
    val rail = remember(date) { RustScheduleCore.dayRail(date) }
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

@Composable
private fun DayHeader(date: String, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dayLabel(date),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Hôm nay",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
