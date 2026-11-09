package com.sv.tnu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.sv.tnu.rust.RustScheduleCore
import java.util.Locale
import kotlin.math.abs

private val weekdayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

private data class WeekDateBounds(
    val week: WeekEntity,
    val start: String,
    val end: String,
)

internal fun weekDates(start: String): List<String> = RustScheduleCore.weekDates(start)

internal fun monthDates(anchor: String, offset: Int): Pair<String, List<String?>> {
    val (month, year, grid) = RustScheduleCore.monthDates(anchor, offset)
        ?: return "" to emptyList()
    val title = String.format(Locale.US, "Tháng %02d/%04d", month, year)
    return title to grid
}

private fun dateInWeek(ymd: String, week: WeekEntity): Boolean {
    val start = week.ngayBatDau.substringBefore(" ").trim().let { normalizePortalDay(it) }
    val end = week.ngayKetThuc.substringBefore(" ").trim().let { normalizePortalDay(it) }
    return ymd in start..end
}

internal fun normalizePortalDay(value: String): String = RustScheduleCore.normalizeDate(value)

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
    weekend: Boolean = false,
) {
    val isSelected = ymd == selectedYmd
    val isToday = ymd == todayYmd
    val selectedDayColor = MaterialTheme.colorScheme.primary
    val highlightColor by animateColorAsState(
        targetValue = when {
            isSelected -> selectedDayColor
            isToday -> selectedDayColor.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "Selected calendar day",
    )
    val day = ymd.substringAfterLast('-').toIntOrNull()?.toString() ?: "-"
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (weekday != null) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (weekend) 10.sp else 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = highlightColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day,
                fontSize = if (weekend) 17.sp else 19.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    isSelected -> Color.White
                    isToday -> selectedDayColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            // A white dot must sit ON the selected blue circle. Below it
            // would disappear against the light calendar background.
            if (hasSchedule && enabled && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .size(4.dp)
                        .background(Color.White, CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    when {
                        isToday -> Color(0xFF22C55E) // Today's marker stays green, even if selected.
                        hasSchedule && enabled && !isSelected -> selectedDayColor
                        else -> Color.Transparent
                    },
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
    onPickDate: (String, Int) -> Unit,
    onMoveDay: (Int) -> Unit,
) {
    val dates = remember(weekStartYmd) { weekDates(weekStartYmd) }
    val weekBounds = remember(availableWeeks) {
        availableWeeks.map { w ->
            WeekDateBounds(
                week = w,
                start = normalizePortalDay(w.ngayBatDau.substringBefore(" ").trim()),
                end = normalizePortalDay(w.ngayKetThuc.substringBefore(" ").trim()),
            )
        }
    }
    val selectedDayColor = MaterialTheme.colorScheme.primary
    var showMonth by rememberSaveable { mutableStateOf(false) }
    var monthOffset by remember { mutableIntStateOf(0) }
    // Do not reset the viewed month during rapid vertical timeline scrolling.

    // Both the seven-day strip and expanded month use this same one-tap
    // selection path. Close the month only after a valid week is selected.
    fun pickDate(date: String, week: WeekEntity?) {
        if (week == null) return
        onPickDate(date, week.tuanHocKy)
        showMonth = false
    }

    val selectedAnchor = selectedYmd.ifBlank { todayYmd }
    val monthAnchor = selectedAnchor.take(7) + "-01"
    val monthView = remember(monthAnchor, monthOffset) { monthDates(monthAnchor, monthOffset) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(weekStartYmd, showMonth, selectedYmd) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        when {
                            abs(dx) > abs(dy) && abs(dx) > 65f -> {
                                // Swiping the date strip moves ONE day, not
                                // an entire week; the caller clamps semesters.
                                onMoveDay(if (dx < 0f) 1 else -1)
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
                    enabled = ymd >= todayYmd &&
                        weekBounds.any { ymd in it.start..it.end },
                    onClick = {
                        val week = weekBounds.firstOrNull { ymd in it.start..it.end }?.week
                        pickDate(ymd, week)
                    },
                    modifier = Modifier.weight(1f),
                    weekend = index >= 5,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showMonth = !showMonth
                    if (showMonth) monthOffset = 0
                }
                .padding(vertical = 3.dp),
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
                        rowDates.forEachIndexed { dayIndex, ymd ->
                            if (ymd == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val week = weekBounds.firstOrNull { ymd in it.start..it.end }?.week
                                CalendarDay(
                                    ymd = ymd,
                                    weekday = null,
                                    selectedYmd = selectedYmd,
                                    todayYmd = todayYmd,
                                    hasSchedule = ymd in eventDays,
                                    enabled = week != null && ymd >= todayYmd,
                                    onClick = { pickDate(ymd, week) },
                                    modifier = Modifier.weight(1f),
                                    weekend = dayIndex >= 5,
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
