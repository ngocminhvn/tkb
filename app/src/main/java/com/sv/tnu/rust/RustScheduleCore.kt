package com.sv.tnu.rust

import com.sv.tnu.data.WeekEntity
import java.util.TimeZone

/**
 * Rust schedule engine used by the Android application via JNI (`libtkb_core.so`).
 */
object RustScheduleCore {
    init {
        System.loadLibrary("tkb_core")
    }

    private external fun normalizeDateNative(value: String): String
    private external fun examEndTimeNative(start: String, minutes: Int): String
    private external fun moveCalendarDayNative(date: String, direction: Int): String
    private external fun weekDatesNative(start: String): String
    private external fun dayRailNative(date: String): String
    private external fun mondayOfWeekNative(date: String): String
    private external fun monthDatesNative(anchor: String, monthOffset: Int): String
    private external fun dayLabelViNative(date: String): String
    private external fun formatDmyNative(date: String): String
    private external fun formatDmNative(date: String): String
    private external fun addCalendarDaysNative(date: String, deltaDays: Int): String
    private external fun parseHhMmMinutesNative(clock: String): Int
    private external fun formatBytesNative(bytes: Long): String
    private external fun vietnamAutoSyncCutoffNative(nowMillis: Long): Long
    private external fun isVietnamDailyAutoSyncDueNative(nowMillis: Long, lastAttemptMillis: Long): Boolean
    private external fun nextVietnamAutoSyncDelayNative(nowMillis: Long, lastAttemptMillis: Long): Long
    private external fun todayVietnamYmdNative(nowMillis: Long): String
    private external fun findWeekNumberNative(weeksPayload: String, dateYmd: String): Int
    private external fun resolveSelectedWeekNative(weeksPayload: String, manualWeek: Int, todayYmd: String): Int
    private external fun calculateExtendedDayCountNative(currentCount: Int, lastLoadedDay: String, targetDate: String): Int
    private external fun generateDateWindowNative(startYmd: String, count: Int): String
    private external fun currentWeekNumberNative(weeksPayload: String, todayYmd: String): Int
    private external fun adjacentTimetableDayNative(selectedYmd: String, direction: Int, weeksPayload: String): String
    private external fun elasticEdgeOffsetPxNative(distancePx: Float, thresholdPx: Float, direction: Int): Float
    private external fun confirmedEdgeWeekDirectionNative(distancePx: Float, thresholdPx: Float, direction: Int, hasAdjacentWeek: Boolean): Int
    private external fun shouldRecreateTimetableAfterFirstSyncNative(hadCachedWeeks: Boolean, committedWeeksCount: Int, alreadyRecreated: Boolean): Boolean
    private external fun shouldPreferJustSyncedCacheNative(
        observedWeeksEmpty: Boolean,
        observedLessonsEmpty: Boolean,
        hasJustSynced: Boolean,
        justSyncedWeeksNotEmpty: Boolean,
        justSyncedLessonsNotEmpty: Boolean,
    ): Boolean

    private external fun reminderMessageNative(fields: Array<String>): Array<String>
    private external fun localYmdNative(epochMillis: Long, offsetMillis: Int): String
    private external fun formatLessonTimeNative(
        period: Int,
        start: String?,
        end: String,
        compact: Boolean,
    ): String
    private external fun widgetDayFinishedNative(
        nowMinutes: Int,
        lessonEnds: Array<String>,
        examStarts: Array<String>,
        examDurations: IntArray,
    ): Boolean

    fun reminderMessage(rows: List<Pair<String, String>>): Pair<String, String> {
        val fields = ArrayList<String>(rows.size * 2)
        for ((time, label) in rows) {
            fields.add(time)
            fields.add(label)
        }
        val (summary, message) = reminderMessageNative(fields.toTypedArray())
        return summary to message
    }

    fun localTodayYmd(nowMillis: Long = System.currentTimeMillis()): String =
        localYmdNative(nowMillis, TimeZone.getDefault().getOffset(nowMillis))

    fun formatLessonTime(period: Int, start: String?, end: String?, compact: Boolean = false): String =
        formatLessonTimeNative(period, start, end.orEmpty(), compact)

    fun widgetDayFinished(
        nowMinutes: Int,
        lessonEnds: Array<String>,
        examStarts: Array<String>,
        examDurations: IntArray,
    ): Boolean = widgetDayFinishedNative(nowMinutes, lessonEnds, examStarts, examDurations)

    fun normalizeDate(value: String): String = normalizeDateNative(value)

    fun examEndTime(start: String, minutes: Int): String = examEndTimeNative(start, minutes)

    fun moveCalendarDay(date: String, direction: Int): String? =
        if (direction == 0) null else moveCalendarDayNative(date, direction).ifBlank { null }

    fun weekDates(start: String): List<String> =
        weekDatesNative(start).takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()

    /** Day-of-month and weekday label (T2..T7, CN) for a yyyy-MM-dd date. */
    fun dayRail(date: String): Pair<String, String>? {
        val parts = dayRailNative(date).split("|")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    /** Monday (`yyyy-MM-dd`) of the week containing `date`. */
    fun mondayOfWeek(date: String): String? =
        mondayOfWeekNative(date).ifBlank { null }

    /** Add or subtract `deltaDays` calendar days from `date`. */
    fun addCalendarDays(date: String, deltaDays: Int): String? =
        addCalendarDaysNative(date, deltaDays).ifBlank { null }

    /** Format any portal or ISO date string as `dd/MM/yyyy`. */
    fun formatDmy(date: String): String = formatDmyNative(date)

    /** Format any portal or ISO date string as `dd/MM`. */
    fun formatDm(date: String): String = formatDmNative(date)

    /** Full Vietnamese weekday and date label, e.g. `"Thứ 2, 09/11"`. */
    fun dayLabelVi(date: String): String? = dayLabelViNative(date).ifBlank { null }

    /** Parse `HH:mm` into minutes since midnight (`0..1439`), or `null` if invalid. */
    fun parseHhMmMinutes(clock: String): Int? =
        parseHhMmMinutesNative(clock).takeIf { it >= 0 }

    /** Format byte size as human-readable `KB` or `MB`. */
    fun formatBytes(bytes: Long): String = formatBytesNative(bytes)

    fun vietnamAutoSyncCutoff(nowMillis: Long): Long = vietnamAutoSyncCutoffNative(nowMillis)

    fun isVietnamDailyAutoSyncDue(nowMillis: Long, lastAttemptMillis: Long): Boolean =
        isVietnamDailyAutoSyncDueNative(nowMillis, lastAttemptMillis)

    fun nextVietnamAutoSyncDelay(nowMillis: Long, lastAttemptMillis: Long): Long =
        nextVietnamAutoSyncDelayNative(nowMillis, lastAttemptMillis)

    /** Compute today's calendar date in Vietnam (UTC+07:00, yyyy-MM-dd). */
    fun todayVietnamYmd(nowMillis: Long = System.currentTimeMillis()): String =
        todayVietnamYmdNative(nowMillis)

    /**
     * Build the Monday-aligned month grid for `anchor` shifted by `monthOffset` months.
     * Returns `(month, year, cells)` where empty cells are `null` and active days are `yyyy-MM-dd`.
     */
    fun monthDates(anchor: String, monthOffset: Int): Triple<Int, Int, List<String?>>? {
        val raw = monthDatesNative(anchor, monthOffset)
        if (raw.isBlank()) return null
        val parts = raw.split("|", limit = 3)
        if (parts.size != 3) return null
        val month = parts[0].toIntOrNull() ?: return null
        val year = parts[1].toIntOrNull() ?: return null
        val cells = parts[2].split(",").map { it.ifEmpty { null } }
        return Triple(month, year, cells)
    }

    private fun packWeeks(weeks: List<WeekEntity>): String =
        weeks.joinToString(";") { "${it.tuanHocKy}|${it.ngayBatDau}|${it.ngayKetThuc}" }

    fun findWeekNumber(weeks: List<WeekEntity>, dateYmd: String): Int? {
        if (weeks.isEmpty()) return null
        return findWeekNumberNative(packWeeks(weeks), dateYmd).takeIf { it > 0 }
    }

    fun currentWeekNumber(weeks: List<WeekEntity>, todayYmd: String): Int? {
        if (weeks.isEmpty()) return null
        return currentWeekNumberNative(packWeeks(weeks), todayYmd).takeIf { it > 0 }
    }

    fun resolveSelectedWeek(weeks: List<WeekEntity>, manualWeek: Int?, todayYmd: String): Int? {
        if (weeks.isEmpty()) return null
        return resolveSelectedWeekNative(packWeeks(weeks), manualWeek ?: 0, todayYmd).takeIf { it > 0 }
    }

    fun calculateExtendedDayCount(currentCount: Int, lastLoadedDay: String, targetDate: String): Int =
        calculateExtendedDayCountNative(currentCount, lastLoadedDay, targetDate)

    fun generateDateWindow(startYmd: String, count: Int): List<String> =
        generateDateWindowNative(startYmd, count).takeIf { it.isNotBlank() }?.split(",") ?: emptyList()

    fun adjacentTimetableDay(
        selectedYmd: String,
        direction: Int,
        weeks: List<WeekEntity>,
    ): Pair<String, Int>? {
        if (direction == 0 || weeks.isEmpty()) return null
        val raw = adjacentTimetableDayNative(selectedYmd, direction, packWeeks(weeks))
        if (raw.isBlank()) return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val date = parts[0]
        val week = parts[1].toIntOrNull() ?: return null
        return date to week
    }

    fun elasticEdgeOffsetPx(distancePx: Float, thresholdPx: Float, direction: Int): Float =
        elasticEdgeOffsetPxNative(distancePx, thresholdPx, direction)

    fun confirmedEdgeWeekDirection(
        distancePx: Float,
        thresholdPx: Float,
        direction: Int,
        hasAdjacentWeek: Boolean,
    ): Int? =
        confirmedEdgeWeekDirectionNative(distancePx, thresholdPx, direction, hasAdjacentWeek).takeIf { it != 0 }

    fun shouldRecreateTimetableAfterFirstSync(
        hadCachedWeeksAtLaunch: Boolean,
        committedWeeksCount: Int,
        alreadyRecreated: Boolean,
    ): Boolean =
        shouldRecreateTimetableAfterFirstSyncNative(hadCachedWeeksAtLaunch, committedWeeksCount, alreadyRecreated)

    fun shouldPreferJustSyncedCache(
        observedWeeksEmpty: Boolean,
        observedLessonsEmpty: Boolean,
        hasJustSynced: Boolean,
        justSyncedWeeksNotEmpty: Boolean,
        justSyncedLessonsNotEmpty: Boolean,
    ): Boolean =
        shouldPreferJustSyncedCacheNative(
            observedWeeksEmpty,
            observedLessonsEmpty,
            hasJustSynced,
            justSyncedWeeksNotEmpty,
            justSyncedLessonsNotEmpty,
        )
}
