package com.sv.tnu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.sv.tnu.MainActivity
import com.sv.tnu.R
import com.sv.tnu.data.AppDatabase
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS_DAY -> changeDay(context, intent, -1)
            ACTION_NEXT_DAY -> changeDay(context, intent, 1)
            ACTION_TODAY -> changeDay(context, intent, 0, reset = true)
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = widgetPreferences(context).edit()
        appWidgetIds.forEach { editor.remove(offsetKey(it)) }
        editor.apply()
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).tkbDao()
                val todayCalendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = dateFormat.format(todayCalendar.time)
                val periods = dao.periodsOnce()
                val todayLessons = dao.lessonsOn(date)
                val preferences = widgetPreferences(context)
                val storedOffset = preferences.let {
                    val key = offsetKey(appWidgetId)
                    if (it.contains(key)) it.getInt(key, 0) else null
                }
                val offset = (storedOffset ?: if (
                    todayLessons.isEmpty() || isDayFinished(todayLessons, periods)
                ) 1 else 0).coerceIn(MIN_OFFSET, MAX_OFFSET)
                val selectedDay = (todayCalendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                val selectedDate = dateFormat.format(selectedDay.time)
                val schedule = WidgetSchedule(
                    title = scheduleTitle(selectedDay, offset),
                    lessons = if (offset == 0) todayLessons else dao.lessonsOn(selectedDate),
                    offset = offset,
                )
                if (storedOffset == null) {
                    preferences.edit().putInt(offsetKey(appWidgetId), schedule.offset).apply()
                }
                val options = manager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                val availableHeight = maxOf(minHeight, maxHeight)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val layoutMode = when {
                    minWidth <= 220 || minWidth <= minHeight + 32 -> LayoutMode.SQUARE
                    minWidth < 320 -> LayoutMode.MEDIUM
                    else -> LayoutMode.WIDE
                }
                val rowLimit = when (layoutMode) {
                    LayoutMode.SQUARE -> when {
                        availableHeight >= 230 -> 3
                        availableHeight >= 120 -> 2
                        else -> 1
                    }
                    LayoutMode.MEDIUM -> when {
                        availableHeight >= 200 -> 3
                        availableHeight >= 110 -> 2
                        else -> 1
                    }
                    LayoutMode.WIDE -> when {
                        availableHeight >= 220 -> 3
                        availableHeight >= 120 -> 2
                        else -> 1
                    }
                }
                manager.updateAppWidget(
                    appWidgetId,
                    buildViews(context, appWidgetId, schedule, periods, rowLimit, layoutMode),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        schedule: WidgetSchedule,
        periods: List<PeriodEntity>,
        rowLimit: Int,
        layoutMode: LayoutMode,
    ): RemoteViews {
        val layout = if (layoutMode == LayoutMode.SQUARE) {
            R.layout.widget_timetable_square
        } else {
            R.layout.widget_timetable
        }
        val views = RemoteViews(context.packageName, layout)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val previousDay = navigationPendingIntent(context, appWidgetId, ACTION_PREVIOUS_DAY, 2)
        val today = navigationPendingIntent(context, appWidgetId, ACTION_TODAY, 3)
        val nextDay = navigationPendingIntent(context, appWidgetId, ACTION_NEXT_DAY, 4)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_previous_day, previousDay)
        views.setOnClickPendingIntent(R.id.widget_title, today)
        views.setOnClickPendingIntent(R.id.widget_next_day, nextDay)
        views.setTextViewText(R.id.widget_title, schedule.title)
        val horizontalPadding = dp(
            context,
            when (layoutMode) {
                LayoutMode.SQUARE -> 10
                LayoutMode.MEDIUM -> 12
                LayoutMode.WIDE -> 16
            },
        )
        val verticalPadding = dp(context, if (layoutMode == LayoutMode.WIDE) 12 else 10)
        views.setViewPadding(
            R.id.widget_root,
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            verticalPadding,
        )
        val titleSize = when (layoutMode) {
            LayoutMode.SQUARE -> 12f
            LayoutMode.MEDIUM -> 13f
            LayoutMode.WIDE -> 14f
        }
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

        val rows = listOf(
            Triple(R.id.lesson_1, R.id.lesson_1_title, R.id.lesson_1_detail),
            Triple(R.id.lesson_2, R.id.lesson_2_title, R.id.lesson_2_detail),
            Triple(R.id.lesson_3, R.id.lesson_3_title, R.id.lesson_3_detail),
        )
        rows.forEachIndexed { index, (containerId, titleId, detailId) ->
            val lesson = schedule.lessons.getOrNull(index).takeIf { index < rowLimit }
            if (lesson == null) {
                views.setViewVisibility(containerId, android.view.View.GONE)
            } else {
                views.setViewVisibility(containerId, android.view.View.VISIBLE)
                views.setTextViewText(titleId, lesson.tenMon)
                views.setTextViewText(detailId, lessonTime(lesson, periods))
                if (layoutMode == LayoutMode.MEDIUM) {
                    views.setTextViewTextSize(titleId, TypedValue.COMPLEX_UNIT_SP, 12f)
                    views.setTextViewTextSize(detailId, TypedValue.COMPLEX_UNIT_SP, 11f)
                }
            }
        }
        views.setViewVisibility(
            R.id.widget_empty,
            if (schedule.lessons.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE,
        )
        return views
    }

    private fun isDayFinished(
        lessons: List<LessonEntity>,
        periods: List<PeriodEntity>,
    ): Boolean {
        val lastEndMinutes = lessons.maxOfOrNull { lesson ->
            val lastPeriod = lesson.tietBatDau + lesson.soTiet - 1
            periods.firstOrNull { it.tiet == lastPeriod }
                ?.gioKetThuc
                .toMinutesOrNull()
                ?: Int.MAX_VALUE
        } ?: return true
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return nowMinutes >= lastEndMinutes
    }

    private fun scheduleTitle(day: Calendar, offset: Int): String {
        val date = SimpleDateFormat("dd/MM", Locale("vi", "VN")).format(day.time)
        return when (offset) {
            0 -> "Hôm nay • $date"
            1 -> "Ngày mai • $date"
            else -> SimpleDateFormat("EEEE, dd/MM", Locale("vi", "VN"))
                .format(day.time)
                .replaceFirstChar { it.uppercase() }
        }
    }

    private fun navigationPendingIntent(
        context: Context,
        appWidgetId: Int,
        action: String,
        requestSuffix: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId * 10 + requestSuffix,
        Intent(context, TimetableWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun changeDay(
        context: Context,
        intent: Intent,
        delta: Int,
        reset: Boolean = false,
    ) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val preferences = widgetPreferences(context)
        val key = offsetKey(appWidgetId)
        val nextOffset = if (reset) {
            0
        } else {
            (preferences.getInt(key, 0) + delta).coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
        preferences.edit().putInt(key, nextOffset).apply()
        update(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun widgetPreferences(context: Context) = context.getSharedPreferences(
        WIDGET_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    private fun offsetKey(appWidgetId: Int) = "day_offset_$appWidgetId"

    private fun String?.toMinutesOrNull(): Int? {
        val parts = this?.trim()?.split(":") ?: return null
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].take(2).toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun lessonTime(lesson: LessonEntity, periods: List<PeriodEntity>): String {
        val start = periods.firstOrNull { it.tiet == lesson.tietBatDau }?.gioBatDau
            ?: "Tiết ${lesson.tietBatDau}"
        val lastPeriod = lesson.tietBatDau + lesson.soTiet - 1
        val end = periods.firstOrNull { it.tiet == lastPeriod }?.gioKetThuc
        return if (end.isNullOrBlank()) start else "$start–$end"
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val ACTION_PREVIOUS_DAY = "com.sv.tnu.widget.PREVIOUS_DAY"
        private const val ACTION_NEXT_DAY = "com.sv.tnu.widget.NEXT_DAY"
        private const val ACTION_TODAY = "com.sv.tnu.widget.TODAY"
        private const val WIDGET_PREFERENCES = "timetable_widget_state"
        private const val MIN_OFFSET = -365
        private const val MAX_OFFSET = 365

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimetableWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, TimetableWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    private data class WidgetSchedule(
        val title: String,
        val lessons: List<LessonEntity>,
        val offset: Int,
    )

    private enum class LayoutMode {
        SQUARE,
        MEDIUM,
        WIDE,
    }
}
