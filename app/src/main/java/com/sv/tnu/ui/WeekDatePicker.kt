package com.sv.tnu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sv.tnu.data.WeekEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val weekdayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
private val selectedDayColor = Color(0xFF009F9C)

/** Parse a date from TNUT as a strict local calendar day. */
private fun calendarFor(ymd: String): Calendar? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = runCatching { format.parse(ymd) }.getOrNull() ?: return null
    return Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun calendarYmd(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

internal fun weekDates(start: String): List<String> {
    val calendar = calendarFor(start) ?: return emptyList()
    return List(7) {
        val day = calendarYmd(calendar.time)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        day
    }
}

internal fun monthDates(anchor: String, offset: Int): Pair<String, List<String?>> {
    val calendar = calendarFor(anchor) ?: return "" to emptyList()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.add(Calendar.MONTH, offset)
    val title = SimpleDateFormat("'Tháng' MM/yyyy", Locale("vi", "VN")).format(calendar.time)
    val leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val total = ((leadingBlanks + days + 6) / 7) * 7
    val grid = List(total) { index ->
        val dayOfMonth = index - leadingBlanks + 1
        if (dayOfMonth !in 1..days) null
        else calendarFor(calendarYmd(calendar.time))?.apply {
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }?.let { calendarYmd(it.time) }
    }
    return title to grid
}

private fun dateInWeek(ymd: String, week: WeekEntity): Boolean {
    val start = week.ngayBatDau.substringBefore(" ").trim().let { normalizePortalDay(it) }
    val end = week.ngayKetThuc.substringBefore(" ").trim().let { normalizePortalDay(it) }
    return ymd in start..end
}

internal fun normalizePortalDay(value: String): String {
    val bits = value.split("/")
    return if (bits.size == 3) {
        "${bits[2]}-${bits[1].padStart(2, '0')}-${bits[0].padStart(2, '0')}"
    } else value.take(10)
}

@Composable
private fun CalendarDay(
    ymd: String,
    weekday: String?,
    selectedYmd: String,
    todayYmd: String,
    hasSchedule: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = ymd == selectedYmd
    val isToday = ymd == todayYmd
    val day = ymd.substringAfterLast('-').toIntOrNull()?.toString() ?: "-"
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (weekday != null) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (isSelected) selectedDayColor
                        else if (isToday) selectedDayColor.copy(alpha = 0.15f)
                        else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day,
                fontSize = 23.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    isSelected -> Color.White
                    isToday -> selectedDayColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    if (hasSchedule && enabled) Color(0xFF22A74C) else Color.Transparent,
                    CircleShape,
                ),
        )
    }
}

/** Seven-day schedule picker; drag sideways to change weeks or down for month view. */
@Composable
fun WeekDatePicker(
    weekStartYmd: String,
    selectedYmd: String,
    todayYmd: String,
    eventDays: Set<String>,
    availableWeeks: List<WeekEntity>,
    currentWeekNumber: Int?,
    onPickDate: (String, Int) -> Unit,
    onMoveWeek: (Int) -> Unit,
) {
    val dates = remember(weekStartYmd) { weekDates(weekStartYmd) }
    val activeWeekIndex = availableWeeks.indexOfFirst { it.tuanHocKy == currentWeekNumber }
    val canGoBack = activeWeekIndex > 0
    val canGoForward = activeWeekIndex in 0 until availableWeeks.lastIndex
    var showMonth by rememberSaveable { mutableStateOf(false) }
    var monthOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(weekStartYmd) { monthOffset = 0 }

    val selectedAnchor = selectedYmd.ifBlank { todayYmd }
    val monthView = remember(selectedAnchor, monthOffset) { monthDates(selectedAnchor, monthOffset) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(weekStartYmd, showMonth, canGoBack, canGoForward) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        when {
                            abs(dx) > abs(dy) && abs(dx) > 65f -> {
                                if (dx < 0 && canGoForward) onMoveWeek(1)
                                if (dx > 0 && canGoBack) onMoveWeek(-1)
                            }
                            dy > 55f -> showMonth = true
                            dy < -55f -> showMonth = false
                        }
                    },
                    onDrag = { change, amount ->
                        dx += amount.x
                        dy += amount.y
                        change.consume()
                    },
                )
            },
    ) {
        // Dates and weekday labels follow the phone's real local calendar.
        Row(modifier = Modifier.fillMaxWidth()) {
            dates.forEachIndexed { index, ymd ->
                CalendarDay(
                    ymd = ymd,
                    weekday = weekdayLabels[index],
                    selectedYmd = selectedYmd,
                    todayYmd = todayYmd,
                    hasSchedule = ymd in eventDays,
                    enabled = true,
                    onClick = {
                        val week = availableWeeks.firstOrNull { dateInWeek(ymd, it) }
                        if (week != null) onPickDate(ymd, week.tuanHocKy)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMonth = !showMonth }
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showMonth) "Thu gọn lịch tháng" else "Vuốt xuống để mở lịch tháng",
                color = selectedDayColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Icon(
                imageVector = if (showMonth) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                contentDescription = if (showMonth) "Thu gọn" else "Mở lịch tháng",
                tint = selectedDayColor,
                modifier = Modifier.size(21.dp),
            )
        }

        AnimatedVisibility(visible = showMonth) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { monthOffset-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tháng trước")
                    }
                    Text(monthView.first, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { monthOffset++ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Tháng sau")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayLabels.forEach { label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                monthView.second.chunked(7).forEach { rowDates ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowDates.forEach { ymd ->
                            if (ymd == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val week = availableWeeks.firstOrNull { dateInWeek(ymd, it) }
                                CalendarDay(
                                    ymd = ymd,
                                    weekday = null,
                                    selectedYmd = selectedYmd,
                                    todayYmd = todayYmd,
                                    hasSchedule = ymd in eventDays,
                                    enabled = week != null,
                                    onClick = {
                                        if (week != null) {
                                            onPickDate(ymd, week.tuanHocKy)
                                            showMonth = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
