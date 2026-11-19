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
import com.sv.tnu.data.AuthStore
import com.sv.tnu.rust.RustScheduleCore
import java.util.Calendar
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
                val date = RustScheduleCore.localTodayYmd()
                val preferences = widgetPreferences(context)
                val storedOffset = preferences.let {
                    val key = offsetKey(appWidgetId)
                    if (it.contains(key)) it.getInt(key, 0) else null
                }

                val options = manager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                val availableHeight = maxOf(minHeight, maxHeight)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val layoutMode = LayoutMode.entries[RustScheduleCore.widgetLayout(minWidth, minHeight)]
                val isSquare = layoutMode == LayoutMode.SQUARE

                val now = Calendar.getInstance()
                val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val dbPath = context.applicationContext.getDatabasePath("tkb.db").absolutePath

                val wire = RustScheduleCore.loadWidgetSchedule(
                    dbPath = dbPath,
                    todayYmd = date,
                    storedOffset = storedOffset,
                    isSquare = isSquare,
                    nowMinutes = nowMinutes,
                ) ?: return@launch

                val schedule = WidgetSchedule(
                    title = wire.title,
                    items = wire.items.map { WidgetItem(it.title, it.time, it.isExam) },
                    offset = wire.offset,
                )
                if (storedOffset == null) {
                    preferences.edit().putInt(offsetKey(appWidgetId), schedule.offset).apply()
                }
                manager.updateAppWidget(
                    appWidgetId,
                    buildViews(context, appWidgetId, schedule, availableHeight, layoutMode),
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
        availableHeight: Int,
        layoutMode: LayoutMode,
    ): RemoteViews {
        val layout = if (layoutMode == LayoutMode.SQUARE) {
            R.layout.widget_timetable_square
        } else {
            R.layout.widget_timetable
        }
        val views = RemoteViews(context.packageName, layout)
        val isDark = try {
            AuthStore(context).isDarkMode
        } catch (_: Exception) {
            false
        }

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

        // Đồng bộ màu nền và chữ theo Dark / Light mode của ứng dụng
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
        )
        views.setTextColor(R.id.widget_previous_day, 0xFF0A84FF.toInt())
        views.setTextColor(R.id.widget_next_day, 0xFF0A84FF.toInt())
        views.setTextColor(
            R.id.widget_title,
            if (isDark) 0xFFF0F2F5.toInt() else 0xFF111418.toInt(),
        )
        views.setTextColor(
            R.id.widget_empty,
            if (isDark) 0xFF8E9AA8.toInt() else 0xFF5B6470.toInt(),
        )
        views.setTextViewText(R.id.widget_empty, "Không có lịch học hoặc lịch thi")

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

        views.removeAllViews(R.id.widget_lessons_container)
        val density = lessonDensity(
            lessonCount = schedule.items.size,
            availableHeight = availableHeight,
            layoutMode = layoutMode,
        )
        schedule.items.forEach { itemData ->
            val itemLayout = if (layoutMode == LayoutMode.SQUARE) {
                R.layout.widget_lesson_item_square
            } else {
                R.layout.widget_lesson_item_horizontal
            }
            val item = RemoteViews(context.packageName, itemLayout).apply {
                setTextViewText(R.id.widget_lesson_subject, itemData.title)
                setTextViewText(R.id.widget_lesson_time, itemData.time)

                // Lịch thi màu đỏ, lịch học bình thường màu xanh dương
                val timeColor = if (itemData.isExam) {
                    if (isDark) 0xFFFF5252.toInt() else 0xFFD32F2F.toInt()
                } else {
                    0xFF0A84FF.toInt()
                }
                val subjectColor = if (isDark) {
                    if (itemData.isExam) 0xFFFFF0F0.toInt() else 0xFFF0F2F5.toInt()
                } else {
                    0xFF111418.toInt()
                }
                val bgResource = if (itemData.isExam) {
                    if (isDark) R.drawable.widget_exam_item_bg_dark else R.drawable.widget_exam_item_bg_light
                } else {
                    if (isDark) R.drawable.widget_item_bg_dark else R.drawable.widget_item_bg_light
                }

                setTextColor(R.id.widget_lesson_time, timeColor)
                setTextColor(R.id.widget_lesson_subject, subjectColor)
                setInt(R.id.widget_lesson_item_root, "setBackgroundResource", bgResource)

                setTextViewTextSize(
                    R.id.widget_lesson_subject,
                    TypedValue.COMPLEX_UNIT_SP,
                    density.subjectSize,
                )
                setTextViewTextSize(
                    R.id.widget_lesson_time,
                    TypedValue.COMPLEX_UNIT_SP,
                    density.timeSize,
                )
                setViewPadding(
                    R.id.widget_lesson_item_root,
                    dp(context, density.horizontalPadding),
                    dp(context, density.verticalPadding),
                    dp(context, density.horizontalPadding),
                    dp(context, density.verticalPadding),
                )
            }
            views.addView(R.id.widget_lessons_container, item)
        }
        views.setViewVisibility(
            R.id.widget_empty,
            if (schedule.items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE,
        )
        return views
    }

    private fun lessonDensity(
        lessonCount: Int,
        availableHeight: Int,
        layoutMode: LayoutMode,
    ): LessonDensity {
        val (subject, time, horizontal, vertical) =
            RustScheduleCore.widgetDensity(lessonCount, availableHeight, layoutMode.ordinal)
        return LessonDensity(subject.toFloat(), time.toFloat(), horizontal, vertical)
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

    private data class WidgetItem(
        val title: String,
        val time: String,
        val isExam: Boolean,
    )

    private data class WidgetSchedule(
        val title: String,
        val items: List<WidgetItem>,
        val offset: Int,
    )

    private enum class LayoutMode {
        SQUARE,
        MEDIUM,
        WIDE,
    }

    private data class LessonDensity(
        val subjectSize: Float,
        val timeSize: Float,
        val horizontalPadding: Int,
        val verticalPadding: Int,
    )
}
