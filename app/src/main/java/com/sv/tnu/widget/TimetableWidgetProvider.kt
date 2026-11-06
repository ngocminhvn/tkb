package com.sv.tnu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.sv.tnu.MainActivity
import com.sv.tnu.R
import com.sv.tnu.data.AppDatabase
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.tnut.TnutApiClient
import com.sv.tnu.tnut.TnutRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
        if (intent.action == ACTION_REFRESH) {
            refreshFromPortal(context)
        } else if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            updateAll(context)
        }
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
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val periods = dao.periodsOnce()
                val schedule = selectSchedule(
                    today = date,
                    lessons = dao.lessonsFrom(date),
                    periods = periods,
                )
                val minHeight = manager.getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val rowLimit = when {
                    minHeight >= 250 -> 3
                    minHeight >= 180 -> 2
                    else -> 1
                }
                manager.updateAppWidget(
                    appWidgetId,
                    buildViews(context, schedule, periods, rowLimit),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        schedule: WidgetSchedule,
        periods: List<PeriodEntity>,
        rowLimit: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timetable)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val refresh = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TimetableWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        views.setTextViewText(R.id.widget_title, schedule.title)
        views.setTextViewText(R.id.widget_refresh, "↻")

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
                views.setTextViewText(detailId, lessonDetail(lesson, periods))
            }
        }
        views.setViewVisibility(
            R.id.widget_empty,
            if (schedule.lessons.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE,
        )
        return views
    }

    private fun selectSchedule(
        today: String,
        lessons: List<LessonEntity>,
        periods: List<PeriodEntity>,
    ): WidgetSchedule {
        val grouped = lessons.groupBy { it.ngayHoc.take(10) }.toSortedMap()
        val todayLessons = grouped[today].orEmpty()
        val todayFinished = todayLessons.isEmpty() || isDayFinished(todayLessons, periods)
        val selectedDate = if (!todayFinished) {
            today
        } else {
            grouped.keys.firstOrNull { it > today }
        }
        return if (selectedDate == null) {
            WidgetSchedule(
                title = "Sắp tới",
                lessons = emptyList(),
            )
        } else {
            WidgetSchedule(
                title = scheduleTitle(selectedDate, today),
                lessons = grouped[selectedDate].orEmpty(),
            )
        }
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

    private fun scheduleTitle(date: String, today: String): String {
        if (date == today) return "Hôm nay"
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        if (date == tomorrowString) return "Ngày mai"
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        }.getOrNull() ?: return date
        return SimpleDateFormat("EEEE, dd/MM", Locale("vi", "VN"))
            .format(parsed)
            .replaceFirstChar { it.uppercase() }
    }

    private fun String?.toMinutesOrNull(): Int? {
        val parts = this?.trim()?.split(":") ?: return null
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].take(2).toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun refreshFromPortal(context: Context) {
        setRefreshing(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authStore = AuthStore(context)
                if (authStore.isLoggedIn) {
                    val dao = AppDatabase.get(context).tkbDao()
                    val repository = TnutRepository(
                        context = context,
                        api = TnutApiClient.create(authStore),
                        dao = dao,
                        authStore = authStore,
                    )
                    val hocKy = authStore.hocKy.takeIf { it != 0 } ?: repository.defaultHocKy
                    repository.refresh(hocKy)
                }
            } finally {
                updateAll(context)
                pendingResult.finish()
            }
        }
    }

    private fun setRefreshing(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TimetableWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            views.setTextViewText(R.id.widget_refresh, "…")
            manager.partiallyUpdateAppWidget(id, views)
        }
    }

    private fun lessonDetail(lesson: LessonEntity, periods: List<PeriodEntity>): String {
        val start = periods.firstOrNull { it.tiet == lesson.tietBatDau }?.gioBatDau
            ?: "Tiết ${lesson.tietBatDau}"
        val lastPeriod = lesson.tietBatDau + lesson.soTiet - 1
        val end = periods.firstOrNull { it.tiet == lastPeriod }?.gioKetThuc
        val time = if (end.isNullOrBlank()) start else "$start–$end"
        return if (lesson.maPhong.isBlank()) time else "$time  •  ${lesson.maPhong}"
    }

    companion object {
        private const val ACTION_REFRESH = "com.sv.tnu.widget.REFRESH"

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
    )
}
