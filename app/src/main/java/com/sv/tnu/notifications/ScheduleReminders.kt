package com.sv.tnu.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sv.tnu.MainActivity
import com.sv.tnu.data.AppDatabase
import com.sv.tnu.data.AuthStore
import com.sv.tnu.rust.RustScheduleCore
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Two independent, opt-in local reminders. RTC alarms use the phone's current
 * time zone and work without a resident service or network activity.
 *
 * These are INEXACT alarms: power-saving restrictions may delay delivery.
 */
object ScheduleReminders {
    const val ACTION_TODAY = "com.sv.tnu.action.REMIND_TODAY"
    const val ACTION_TOMORROW = "com.sv.tnu.action.REMIND_TOMORROW"
    const val CHANNEL = "schedule_reminders"
    private const val TODAY_REQUEST = 301
    private const val TOMORROW_REQUEST = 302

    fun syncAll(context: Context) {
        sync(context, ACTION_TODAY)
        sync(context, ACTION_TOMORROW)
    }

    fun cancelAll(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(intent(context, ACTION_TODAY))
        alarm.cancel(intent(context, ACTION_TOMORROW))
    }

    fun sync(context: Context, kind: String) {
        if (kind != ACTION_TODAY && kind != ACTION_TOMORROW) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = intent(context, kind)
        alarm.cancel(pending)
        val store = AuthStore(context)
        val enabled = if (kind == ACTION_TODAY) store.remindToday else store.remindTomorrow
        if (!enabled || !store.isLoggedIn) return

        val minute = if (kind == ACTION_TODAY) store.todayReminderMinutes
            else store.tomorrowReminderMinutes
        val trigger = nextAlarmMillis(System.currentTimeMillis(), minute)
        try {
            // Inexact on purpose: no special exact-alarm permission, lower cost.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (error: SecurityException) {
            Log.w("ScheduleReminders", "Cannot schedule reminder", error)
        }
    }

    /** Next local wall-clock occurrence of HH:mm, strictly after now. */
    internal fun nextAlarmMillis(now: Long, minuteOfDay: Int): Long {
        val h = (minuteOfDay.coerceIn(0, 1439)) / 60
        val m = (minuteOfDay.coerceIn(0, 1439)) % 60
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    private fun intent(context: Context, kind: String): PendingIntent {
        val code = if (kind == ACTION_TODAY) TODAY_REQUEST else TOMORROW_REQUEST
        return PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ScheduleReminderReceiver::class.java).setAction(kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    suspend fun show(context: Context, kind: String) {
        val store = AuthStore(context)
        if (!store.isLoggedIn || (kind == ACTION_TODAY && !store.remindToday) ||
            (kind == ACTION_TOMORROW && !store.remindTomorrow)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return

        val date = Calendar.getInstance()
        val isTomorrow = kind == ACTION_TOMORROW
        val todayYmd = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH) + 1,
            date.get(Calendar.DAY_OF_MONTH),
        )
        val target = if (isTomorrow) {
            RustScheduleCore.addCalendarDays(todayYmd, 1) ?: todayYmd
        } else {
            todayYmd
        }
        val displayDate = RustScheduleCore.formatDmy(target)
        val dao = AppDatabase.get(context).tkbDao()
        // Never synthesize a semester: only notify for one the portal stored.
        val semester = store.hocKy.takeIf { it != 0 } ?: return
        val lessons = dao.reminderLessonsOn(semester, target)
        val exams = dao.reminderExamsOn(semester, target)
        val periods = dao.periodsOnce()
        val contents = buildList {
            lessons.forEach { lesson ->
                val hour = periods.firstOrNull { it.tiet == lesson.tietBatDau }?.gioBatDau
                    ?: "Tiết ${lesson.tietBatDau}"
                add(hour to "${lesson.tenMon} • ${lesson.maPhong}")
            }
            exams.forEach { exam ->
                add(exam.gioBatDau to "THI: ${exam.tenMon} • ${exam.maPhong}")
            }
        }.sortedBy { it.first }
            .map { (time, label) -> "$time  $label" }
        val message = if (contents.isEmpty()) {
            "Không có lịch học hoặc lịch thi."
        } else {
            contents.take(7).joinToString("\n") +
                (if (contents.size > 7) "\nVà ${contents.size - 7} buổi khác" else "")
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Nhắc thời khóa biểu",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Lịch hôm nay và ngày mai theo giờ đã chọn" }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (isTomorrow) "Lịch ngày mai • $displayDate"
            else "Lịch hôm nay • $displayDate"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contents.firstOrNull() ?: message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(if (isTomorrow) TOMORROW_REQUEST else TODAY_REQUEST, notification)
    }
}

class ScheduleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.action
        if (kind != ScheduleReminders.ACTION_TODAY &&
            kind != ScheduleReminders.ACTION_TOMORROW) return
        // Register tomorrow's alarm before reading Room so the reminder won't
        // silently stop after a process death or failed database read.
        ScheduleReminders.sync(context, kind)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleReminders.show(context.applicationContext, kind)
            } catch (error: Exception) {
                Log.e("ScheduleReminders", "Reminder failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> ScheduleReminders.syncAll(context)
        }
    }
}
