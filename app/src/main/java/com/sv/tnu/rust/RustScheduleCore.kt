package com.sv.tnu.rust

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Rust schedule engine used by the Android application.
 * Android must ship libtkb_core.so. Only plain JVM unit tests use Kotlin
 * equivalents because they run without an Android NDK library.
 */
object RustScheduleCore {
    private val nativeReady: Boolean = try {
        System.loadLibrary("tkb_core")
        true
    } catch (error: UnsatisfiedLinkError) {
        val androidRuntime = System.getProperty("java.vm.name", "")
            .contains("Dalvik", ignoreCase = true)
        if (androidRuntime) throw error
        false
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

    fun normalizeDate(value: String): String {
        if (nativeReady) return normalizeDateNative(value)
        val clean = value.trim().substringBefore(" ").substringBefore("T")
        val slash = clean.split("/")
        if (slash.size == 3) return "${slash[2]}-${slash[1].padStart(2, '0')}-${slash[0].padStart(2, '0')}"
        val dash = clean.split("-")
        if (dash.size == 3) {
            return if (dash[0].length == 4) {
                "${dash[0]}-${dash[1].padStart(2, '0')}-${dash[2].padStart(2, '0')}"
            } else {
                "${dash[2]}-${dash[1].padStart(2, '0')}-${dash[0].padStart(2, '0')}"
            }
        }
        return clean
    }

    fun examEndTime(start: String, minutes: Int): String {
        if (nativeReady) return examEndTimeNative(start, minutes)
        if (minutes <= 0) return ""
        return runCatching {
            val values = start.split(":")
            require(values.size == 2)
            val hour = values[0].trim().toInt()
            val minute = values[1].trim().toInt()
            require(hour in 0..23 && minute in 0..59)
            val total = (hour * 60 + minute + minutes) % 1440
            String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
        }.getOrDefault("")
    }

    fun moveCalendarDay(date: String, direction: Int): String? {
        if (direction == 0) return null
        if (nativeReady) return moveCalendarDayNative(date, direction).ifBlank { null }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(date) }.getOrNull() ?: return null
        return Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, if (direction > 0) 1 else -1)
        }.let { format.format(it.time) }
    }

    fun weekDates(start: String): List<String> {
        if (nativeReady) return weekDatesNative(start).takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(start) }.getOrNull() ?: return emptyList()
        val calendar = Calendar.getInstance().apply { time = parsed }
        return List(7) {
            val day = format.format(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            day
        }
    }

    /** Day-of-month and weekday label (T2..T7, CN) for a yyyy-MM-dd date. */
    fun dayRail(date: String): Pair<String, String>? {
        if (nativeReady) {
            val parts = dayRailNative(date).split("|")
            return if (parts.size == 2) parts[0] to parts[1] else null
        }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(normalizeDate(date)) }.getOrNull() ?: return null
        val calendar = Calendar.getInstance().apply { time = parsed }
        val label = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }
        return calendar.get(Calendar.DAY_OF_MONTH).toString() to label
    }

    /** Monday (`yyyy-MM-dd`) of the week containing `date`. */
    fun mondayOfWeek(date: String): String? {
        if (nativeReady) return mondayOfWeekNative(date).ifBlank { null }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(normalizeDate(date)) }.getOrNull() ?: return null
        val calendar = Calendar.getInstance().apply { time = parsed }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val offsetToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        calendar.add(Calendar.DAY_OF_MONTH, offsetToMonday)
        return format.format(calendar.time)
    }

    /** Add or subtract `deltaDays` calendar days from `date`. */
    fun addCalendarDays(date: String, deltaDays: Int): String? {
        if (nativeReady) return addCalendarDaysNative(date, deltaDays).ifBlank { null }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(normalizeDate(date)) }.getOrNull() ?: return null
        return Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, deltaDays)
        }.let { format.format(it.time) }
    }

    /** Format any portal or ISO date string as `dd/MM/yyyy`. */
    fun formatDmy(date: String): String {
        if (nativeReady) return formatDmyNative(date)
        val iso = normalizeDate(date)
        val parts = iso.split("-")
        return if (parts.size == 3 && parts[0].length == 4) {
            "${parts[2].padStart(2, '0')}/${parts[1].padStart(2, '0')}/${parts[0]}"
        } else {
            date.trim()
        }
    }

    /** Format any portal or ISO date string as `dd/MM`. */
    fun formatDm(date: String): String {
        if (nativeReady) return formatDmNative(date)
        val iso = normalizeDate(date)
        val parts = iso.split("-")
        return if (parts.size == 3 && parts[0].length == 4) {
            "${parts[2].padStart(2, '0')}/${parts[1].padStart(2, '0')}"
        } else {
            date.trim()
        }
    }

    /** Full Vietnamese weekday and date label, e.g. `"Thứ 2, 09/11"`. */
    fun dayLabelVi(date: String): String? {
        if (nativeReady) return dayLabelViNative(date).ifBlank { null }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(normalizeDate(date)) }.getOrNull() ?: return null
        val calendar = Calendar.getInstance().apply { time = parsed }
        val weekday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Thứ 2"
            Calendar.TUESDAY -> "Thứ 3"
            Calendar.WEDNESDAY -> "Thứ 4"
            Calendar.THURSDAY -> "Thứ 5"
            Calendar.FRIDAY -> "Thứ 6"
            Calendar.SATURDAY -> "Thứ 7"
            else -> "Chủ nhật"
        }
        val dm = SimpleDateFormat("dd/MM", Locale.US).format(calendar.time)
        return "$weekday, $dm"
    }

    /** Parse `HH:mm` into minutes since midnight (`0..1439`), or `null` if invalid. */
    fun parseHhMmMinutes(clock: String): Int? {
        if (nativeReady) return parseHhMmMinutesNative(clock).takeIf { it >= 0 }
        val parts = clock.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Format byte size as human-readable `KB` or `MB`. */
    fun formatBytes(bytes: Long): String {
        if (nativeReady) return formatBytesNative(bytes)
        val clamped = bytes.coerceAtLeast(0L)
        val mb = clamped / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", (clamped / 1024.0).coerceAtLeast(1.0))
        }
    }

    /**
     * Build the Monday-aligned month grid for `anchor` shifted by `monthOffset` months.
     * Returns `(month, year, cells)` where empty cells are `null` and active days are `yyyy-MM-dd`.
     */
    fun monthDates(anchor: String, monthOffset: Int): Triple<Int, Int, List<String?>>? {
        if (nativeReady) {
            val raw = monthDatesNative(anchor, monthOffset)
            if (raw.isBlank()) return null
            val parts = raw.split("|", limit = 3)
            if (parts.size != 3) return null
            val month = parts[0].toIntOrNull() ?: return null
            val year = parts[1].toIntOrNull() ?: return null
            val cells = parts[2].split(",").map { it.ifEmpty { null } }
            return Triple(month, year, cells)
        }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = runCatching { format.parse(normalizeDate(anchor)) }.getOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val leadingBlanks = if (firstDow == Calendar.SUNDAY) 6 else firstDow - Calendar.MONDAY
        val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
        val cells = List(totalCells) { index ->
            val day = index - leadingBlanks + 1
            if (day in 1..daysInMonth) {
                cal.set(Calendar.DAY_OF_MONTH, day)
                format.format(cal.time)
            } else {
                null
            }
        }
        return Triple(month, year, cells)
    }
}
