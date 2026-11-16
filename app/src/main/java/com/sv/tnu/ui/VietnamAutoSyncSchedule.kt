package com.sv.tnu.ui

import com.sv.tnu.rust.RustScheduleCore

/** The native schedule engine owns the 06:30 Vietnam-time sync policy. */
internal fun vietnamAutoSyncCutoffMillis(nowMillis: Long): Long =
    RustScheduleCore.vietnamAutoSyncCutoff(nowMillis)

/** At most one attempt per day, after 06:30 Asia/Ho_Chi_Minh. */
internal fun isVietnamDailyAutoSyncDue(nowMillis: Long, lastAttemptMillis: Long): Boolean =
    RustScheduleCore.isVietnamDailyAutoSyncDue(nowMillis, lastAttemptMillis)

/** Delay until the next eligible cutoff, never poll each minute. */
internal fun nextVietnamAutoSyncDelayMillis(nowMillis: Long, lastAttemptMillis: Long): Long =
    RustScheduleCore.nextVietnamAutoSyncDelay(nowMillis, lastAttemptMillis)
