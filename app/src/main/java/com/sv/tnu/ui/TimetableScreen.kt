package com.sv.tnu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sv.tnu.ui.glass.Glass
import com.sv.tnu.ui.glass.GlassIconButton
import com.sv.tnu.ui.glass.GlassTextButton
import com.sv.tnu.ui.glass.glassEffect
import androidx.lifecycle.viewModelScope
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.tnut.TnutRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val repository: TnutRepository,
    private val authStore: AuthStore,
) : ViewModel() {
    val hocKy: Int = authStore.hocKy.takeIf { it != 0 } ?: repository.defaultHocKy

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(authStore.lastSyncTime)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    val weeks: StateFlow<List<WeekEntity>> = repository.weeksFlow(hocKy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periods: StateFlow<List<PeriodEntity>> = repository.periodsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val currentWeekNumber: StateFlow<Int?> = weeks.map { weekList ->
        currentWeekNumber(weekList) ?: weekList.firstOrNull()?.tuanHocKy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Tùy chọn hiển thị số tuần:
     * Ví dụ 4 tuần: (4-1)/2 = 1 tuần trước, 1 tuần hiện tại, 2 tuần sau.
     * Ví dụ 5 tuần: (5-1)/2 = 2 tuần trước, 1 tuần hiện tại, 2 tuần sau.
     */
    val visibleWeeks: StateFlow<List<WeekEntity>> = combine(weeks, currentWeekNumber, _visibleWeekCount) { weekList, curr, count ->
        if (weekList.isEmpty()) emptyList()
        else {
            val center = curr ?: weekList.firstOrNull()?.tuanHocKy ?: 1
            val n = count.coerceIn(1, 20)
            val weeksBefore = (n - 1) / 2
            val weeksAfter = n - 1 - weeksBefore
            val minWeek = center - weeksBefore
            val maxWeek = center + weeksAfter
            weekList.filter { it.tuanHocKy in minWeek..maxWeek }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedWeek: StateFlow<Int?> = combine(visibleWeeks, _selectedWeek) { weekList, selected ->
        val current = currentWeekNumber(weekList) ?: weekList.firstOrNull()?.tuanHocKy
        val validSelected = selected?.takeIf { s -> weekList.any { it.tuanHocKy == s } }
        validSelected ?: current
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lessons: StateFlow<List<LessonEntity>> = selectedWeek
        .flatMapLatest { week ->
            if (week == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.lessonsFlow(hocKy, week)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings counts the whole semester, not just whichever week is selected.
    val semesterLessons: StateFlow<List<LessonEntity>> = repository.semesterLessonsFlow(hocKy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isFetchingExams = MutableStateFlow(false)
    val isFetchingExams: StateFlow<Boolean> = _isFetchingExams.asStateFlow()
    private val _examSyncError = MutableStateFlow<String?>(null)
    val examSyncError: StateFlow<String?> = _examSyncError.asStateFlow()

    // Clean up ONLY the exact known legacy demo-exam fixture before syncing.
    // This does not modify real portal cache, offline lessons or personal notes.
    private val cacheReadyJob = viewModelScope.launch {
        repository.prepareOfflineCache(hocKy)
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

    fun moveWeek(delta: Int) {
        val list = visibleWeeks.value
        if (list.isEmpty()) return
        val current = selectedWeek.value ?: return
        val min = list.minOf { it.tuanHocKy }
        val max = list.maxOf { it.tuanHocKy }
        val target = (current + delta).coerceIn(min, max)
        _selectedWeek.value = target
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

    /** Tuần chứa ngày hôm nay, theo ngay_bat_dau/ngay_ket_thuc dạng dd/MM/yyyy. */
    private fun currentWeekNumber(weekList: List<WeekEntity>): Int? {
        if (weekList.isEmpty()) return null
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        for (week in weekList) {
            val start = runCatching { fmt.parse(week.ngayBatDau.trim().take(10)) }.getOrNull() ?: continue
            val end = runCatching { fmt.parse(week.ngayKetThuc.trim().take(10)) }.getOrNull() ?: continue
            val endCal = Calendar.getInstance().apply {
                time = end
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            if (!today.before(start) && !today.after(endCal.time)) return week.tuanHocKy
        }
        val firstStart = runCatching { fmt.parse(weekList.first().ngayBatDau.trim().take(10)) }.getOrNull()
        if (firstStart != null && today.before(firstStart)) {
            return weekList.first().tuanHocKy
        }
        val lastEnd = runCatching { fmt.parse(weekList.last().ngayKetThuc.trim().take(10)) }.getOrNull()
        if (lastEnd != null && today.after(lastEnd)) {
            return weekList.last().tuanHocKy
        }
        return weekList.firstOrNull()?.tuanHocKy
    }
}

private val dayFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
private val dayLabelFormat = ThreadLocal.withInitial { SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")) }

private fun normalizeDateToYmd(dateStr: String): String {
    val clean = dateStr.trim().substringBefore(" ").substringBefore("T")
    val partsSlash = clean.split("/")
    if (partsSlash.size == 3) {
        val day = partsSlash[0].padStart(2, '0')
        val month = partsSlash[1].padStart(2, '0')
        val year = partsSlash[2]
        return "$year-$month-$day"
    }
    val partsDash = clean.split("-")
    if (partsDash.size == 3) {
        return if (partsDash[0].length == 4) {
            val year = partsDash[0]
            val month = partsDash[1].padStart(2, '0')
            val day = partsDash[2].padStart(2, '0')
            "$year-$month-$day"
        } else {
            val day = partsDash[0].padStart(2, '0')
            val month = partsDash[1].padStart(2, '0')
            val year = partsDash[2]
            "$year-$month-$day"
        }
    }
    return clean
}

private fun localTodayYmd(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private fun dayLabel(ngayHoc: String): String {
    val datePart = normalizeDateToYmd(ngayHoc)
    val date = runCatching { dayFormat.get().parse(datePart) }.getOrNull() ?: return datePart
    return dayLabelFormat.get().format(date).replaceFirstChar { it.uppercase() }
}

private fun calculateExamEndTime(gioBatDau: String, soPhut: Int): String {
    if (soPhut <= 0) return ""
    return try {
        val parts = gioBatDau.split(":")
        val hour = parts[0].trim().toInt()
        val minute = parts[1].trim().toInt()
        val totalMinutes = hour * 60 + minute + soPhut
        val endHour = (totalMinutes / 60) % 24
        val endMinute = totalMinutes % 60
        String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute)
    } catch (_: Exception) {
        ""
    }
}

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
        override val date: String get() = lesson.ngayHoc.take(10)
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
    val weeks by viewModel.visibleWeeks.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val lessonNotes by viewModel.lessonNotes.collectAsState()
    var noteLesson by remember { mutableStateOf<LessonEntity?>(null) }
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark

    // Both lessons and exams share one past-day filter. Re-evaluate the local
    // date when the app resumes after midnight, without background polling.
    var todayStr by remember { mutableStateOf(localTodayYmd()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                todayStr = localTodayYmd()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        todayStr = localTodayYmd()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Mặc định: ngày qua rồi thì không hiện nữa (showPastDays = false)
    var showPastDays by remember { mutableStateOf(false) }

    val weekInfo = weeks.firstOrNull { it.tuanHocKy == selectedWeek }
    val startDayYmd = remember(weekInfo) {
        weekInfo?.let { normalizeDateToYmd(it.ngayBatDau) } ?: ""
    }
    val endDayYmd = remember(weekInfo) {
        weekInfo?.let { normalizeDateToYmd(it.ngayKetThuc) } ?: ""
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

    // Các ngày đã qua trong tuần
    val hasPastDaysInWeek = remember(allGrouped, todayStr) {
        allGrouped.keys.any { it < todayStr }
    }
    val pastDaysCount = remember(allGrouped, todayStr) {
        allGrouped.keys.count { it < todayStr }
    }

    // Danh sách hiển thị theo logic: ngày qua rồi thì không hiện nữa
    val displayedGrouped = remember(allGrouped, showPastDays, todayStr) {
        if (showPastDays) {
            allGrouped
        } else {
            allGrouped.filterKeys { date -> date >= todayStr }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Thời khóa biểu",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Học kỳ ${viewModel.hocKy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Tải lại thời khóa biểu và lịch thi",
                        onClick = { viewModel.refresh() },
                        enabled = !refreshing,
                        loading = refreshing,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Week Selector Card with Liquid Glass
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        glass = Glass(alpha = if (isDark) 0.88f else 0.92f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val minWeek = weeks.minOfOrNull { it.tuanHocKy }
                    val maxWeek = weeks.maxOfOrNull { it.tuanHocKy }
                    val canMovePrev = selectedWeek != null && minWeek != null && selectedWeek!! > minWeek
                    val canMoveNext = selectedWeek != null && maxWeek != null && selectedWeek!! < maxWeek

                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Tuần trước",
                        onClick = { viewModel.moveWeek(-1) },
                        enabled = canMovePrev,
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedWeek != null) "Tuần $selectedWeek" else "—",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = weekInfo?.let { "${it.ngayBatDau} – ${it.ngayKetThuc}" } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }

                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Tuần sau",
                        onClick = { viewModel.moveWeek(1) },
                        enabled = canMoveNext,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle / Notice filter chip for past days
            if (hasPastDaysInWeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .glassEffect(
                                glass = Glass(alpha = if (isDark) 0.66f else 0.80f),
                                shape = RoundedCornerShape(50),
                            )
                            .clickable { showPastDays = !showPastDays }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (showPastDays) Icons.Default.FilterList else Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showPastDays) "Đang hiện cả các ngày đã qua • Ẩn đi"
                                else "Đã ẩn $pastDaysCount ngày đã qua • Chạm để xem",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

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

            // Empty state căn giữa trung tâm
            if (displayedGrouped.isEmpty() && !refreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (allGrouped.isNotEmpty() && !showPastDays) {
                            "Các ngày học của tuần này đã qua."
                        } else {
                            "Tuần này không có lịch học hoặc lịch thi nào."
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Timetable List
            if (displayedGrouped.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                displayedGrouped.forEach { (date, dayItems) ->
                    val isToday = date == todayStr

                    item(key = "header-$date", contentType = "header") {
                        DayHeader(
                            date = date,
                            isToday = isToday,
                        )
                    }

                    items(
                        items = dayItems,
                        key = { item ->
                            when (item) {
                                is ScheduleItem.Lesson -> "lesson-${item.lesson.idTkb}"
                                is ScheduleItem.Exam -> "exam-${item.exam.id}"
                            }
                        },
                        contentType = { item ->
                            when (item) {
                                is ScheduleItem.Lesson -> 0
                                is ScheduleItem.Exam -> 1
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ScheduleItem.Lesson -> LessonCard(
                                lesson = item.lesson,
                                time = item.timeDisplay,
                                isToday = isToday,
                                note = lessonNotes[item.lesson.idTkb].orEmpty(),
                                onEditNote = { noteLesson = item.lesson },
                            )
                            is ScheduleItem.Exam -> ExamCard(
                                exam = item.exam,
                                endTime = item.endTime,
                                isToday = isToday,
                            )
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
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                            .background(MaterialTheme.colorScheme.secondary),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Subject title & credits badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = lesson.tenMon,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Replace the per-lesson credit badge with a single action:
                // the editor is only created after the student taps the pencil.
                GlassIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = if (note.isBlank()) "Thêm ghi chú" else "Sửa ghi chú",
                    onClick = onEditNote,
                    size = 38.dp,
                    iconSize = 18.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Period pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• Tiết ${lesson.tietBatDau} (${lesson.soTiet} tiết)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(4.dp))

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
                    text = "${lesson.maMon} • Nhóm ${lesson.maNhom}${if (lesson.tenLop.isNotBlank()) " • Lớp " + lesson.tenLop else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            // No empty "Thêm ghi chú" row. Only saved notes are displayed.
            // The glass pencil in the title row is always available to edit.
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(
                            glass = Glass(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(onClick = onEditNote)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
            GlassTextButton(text = "Lưu", onClick = { onSave(note.trim()) })
        },
        dismissButton = {
            GlassTextButton(text = "Huỷ", onClick = onDismiss)
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
                Text(
                    text = exam.tenMon,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
