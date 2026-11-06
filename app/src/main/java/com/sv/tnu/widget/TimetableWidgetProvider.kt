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
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import java.text.SimpleDateFormat
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
        if (intent.action == ACTION_REFRESH ||
            intent.action == Intent.ACTION_DATE_CHANGED ||
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
                val lessons = dao.lessonsOn(date)
                val periods = dao.periodsOnce()
                val minHeight = manager.getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val rowLimit = when {
                    minHeight >= 250 -> 3
                    minHeight >= 180 -> 2
                    else -> 1
                }
                manager.updateAppWidget(
                    appWidgetId,
                    buildViews(context, lessons, periods, rowLimit),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        lessons: List<LessonEntity>,
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

        val rows = listOf(
            Triple(R.id.lesson_1, R.id.lesson_1_title, R.id.lesson_1_detail),
            Triple(R.id.lesson_2, R.id.lesson_2_title, R.id.lesson_2_detail),
            Triple(R.id.lesson_3, R.id.lesson_3_title, R.id.lesson_3_detail),
        )
        rows.forEachIndexed { index, (containerId, titleId, detailId) ->
            val lesson = lessons.getOrNull(index).takeIf { index < rowLimit }
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
            if (lessons.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE,
        )
        return views
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
}
