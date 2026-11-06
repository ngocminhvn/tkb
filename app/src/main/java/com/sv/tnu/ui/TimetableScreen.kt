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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sv.tnu.ui.glass.Glass
import com.sv.tnu.ui.glass.glassEffect
import androidx.lifecycle.viewModelScope
import com.sv.tnu.data.AuthStore
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
import kotlinx.coroutines.flow.flatMapLatest
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

    val selectedWeek: StateFlow<Int?> = combine(weeks, _selectedWeek) { weekList, selected ->
        selected ?: currentWeekNumber(weekList) ?: weekList.firstOrNull()?.tuanHocKy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lessons: StateFlow<List<LessonEntity>> = selectedWeek
        .flatMapLatest { week ->
            if (week == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.lessonsFlow(hocKy, week)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedFromAssetsIfEmpty(hocKy)
        }
    }

    fun selectWeek(week: Int) {
        _selectedWeek.value = week
    }

    fun moveWeek(delta: Int) {
        val list = weeks.value
        val current = selectedWeek.value ?: return
        val target = (current + delta).coerceIn(
            list.minOfOrNull { it.tuanHocKy } ?: current,
            list.maxOfOrNull { it.tuanHocKy } ?: current,
        )
        _selectedWeek.value = target
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            repository.refresh(hocKy)
                .onSuccess {
                    val now = System.currentTimeMillis()
                    authStore.lastSyncTime = now
                    _lastSyncTime.value = now
                }
                .onFailure { _error.value = "Không tải được lịch mới: ${it.message ?: "lỗi mạng"}. Đang hiện lịch đã lưu." }
            _refreshing.value = false
        }
    }

    /** Tuần chứa ngày hôm nay, theo ngay_bat_dau/ngay_ket_thuc dạng dd/MM/yyyy. */
    private fun currentWeekNumber(weekList: List<WeekEntity>): Int? {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        for (week in weekList) {
            val start = runCatching { fmt.parse(week.ngayBatDau) }.getOrNull() ?: continue
            val end = runCatching { fmt.parse(week.ngayKetThuc) }.getOrNull() ?: continue
            if (!today.before(start) && !today.after(end)) return week.tuanHocKy
        }
        return null
    }
}

private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val dayLabelFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN"))

private fun dayLabel(ngayHoc: String): String {
    val datePart = ngayHoc.take(10)
    val date = runCatching { dayFormat.parse(datePart) }.getOrNull() ?: return datePart
    return dayLabelFormat.format(date).replaceFirstChar { it.uppercase() }
}

private fun timeRange(periods: List<PeriodEntity>, tietBatDau: Int, soTiet: Int): String {
    val start = periods.firstOrNull { it.tiet == tietBatDau }?.gioBatDau ?: return "Tiết $tietBatDau"
    val endTiet = tietBatDau + soTiet - 1
    val end = periods.firstOrNull { it.tiet == endTiet }?.gioKetThuc ?: ""
    return if (end.isNotBlank()) "$start – $end" else start
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val weeks by viewModel.weeks.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    var askedRefresh by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    // Date logic: Hôm nay và lọc ngày đã qua
    val todayCalendar = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val todayDate = remember(todayCalendar) { todayCalendar.time }
    val todayStr = remember(todayDate) { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayDate) }

    // Mặc định: ngày qua rồi thì không hiện nữa (showPastDays = false)
    var showPastDays by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!askedRefresh) {
            askedRefresh = true
            viewModel.refresh()
        }
    }

    val weekInfo = weeks.firstOrNull { it.tuanHocKy == selectedWeek }
    val allGrouped = remember(lessons) {
        lessons.groupBy { it.ngayHoc.take(10) }.toSortedMap()
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
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !refreshing,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
                        }
                    }
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
                    IconButton(
                        onClick = { viewModel.moveWeek(-1) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tuần trước",
                            modifier = Modifier.size(20.dp),
                        )
                    }

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

                    IconButton(
                        onClick = { viewModel.moveWeek(1) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Tuần sau",
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                            "Tuần này không có buổi học nào."
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
                displayedGrouped.forEach { (date, dayLessons) ->
                    val isToday = date == todayStr

                    item(key = "header-$date") {
                        DayHeader(
                            date = dayLessons.first().ngayHoc,
                            isToday = isToday,
                        )
                    }

                    items(dayLessons, key = { it.idTkb }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            time = timeRange(periods, lesson.tietBatDau, lesson.soTiet),
                            isToday = isToday,
                        )
                    }
                }
            }
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
private fun LessonCard(lesson: LessonEntity, time: String, isToday: Boolean) {
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "${lesson.soTinChi} TC",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
        }
    }
}
