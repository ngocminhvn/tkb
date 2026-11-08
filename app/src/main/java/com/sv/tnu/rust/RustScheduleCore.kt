package com.sv.tnu.rust

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
}
