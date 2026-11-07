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
}
