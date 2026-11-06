package com.sv.tnu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.data.WeekEntity

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
    onLogout: () -> Unit,
) {
    val weeks by viewModel.weeks.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    var askedRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!askedRefresh) {
            askedRefresh = true
            viewModel.refresh()
        }
    }

    val weekInfo = weeks.firstOrNull { it.tuanHocKy == selectedWeek }
    val grouped = remember(lessons) {
        lessons.groupBy { it.ngayHoc.take(10) }.toSortedMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thời khóa biểu") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Đăng xuất")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Học kỳ ${viewModel.hocKy}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.moveWeek(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tuần trước")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selectedWeek != null) "Tuần $selectedWeek" else "—",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = weekInfo?.let { "${it.ngayBatDau} – ${it.ngayKetThuc}" } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { viewModel.moveWeek(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Tuần sau")
                }
            }
            if (refreshing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (grouped.isEmpty() && !refreshing) {
                Text(
                    text = "Tuần này không có buổi học nào.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                grouped.forEach { (date, dayLessons) ->
                    item(key = "day-$date") {
                        Text(
                            text = dayLabel(dayLessons.first().ngayHoc),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(dayLessons, key = { it.idTkb }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            time = timeRange(periods, lesson.tietBatDau, lesson.soTiet),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: LessonEntity, time: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = lesson.tenMon,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$time • Tiết ${lesson.tietBatDau} (${lesson.soTiet} tiết)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${lesson.maMon} • ${lesson.soTinChi} tín chỉ • Nhóm ${lesson.maNhom}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Phòng ${lesson.maPhong.ifBlank { "—" }} • ${lesson.tenGiangVien}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (lesson.tenLop.isNotBlank()) {
                Text(
                    text = "Lớp ${lesson.tenLop}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
