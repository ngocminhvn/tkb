//! Daily auto-sync cutoff for Vietnam (UTC+07:00, no DST in supported modern dates).
const DAY_MS: i64 = 86_400_000;
const VIETNAM_OFFSET_MS: i64 = 7 * 3_600_000;
const SIX_THIRTY_MS: i64 = (6 * 60 + 30) * 60_000;
pub fn cutoff_millis(now_millis: i64) -> i64 {
    let local = now_millis.saturating_add(VIETNAM_OFFSET_MS);
    local.div_euclid(DAY_MS)
        .saturating_mul(DAY_MS)
        .saturating_add(SIX_THIRTY_MS)
        .saturating_sub(VIETNAM_OFFSET_MS)
}
pub fn is_due(now_millis: i64, last_attempt_millis: i64) -> bool {
    let cutoff = cutoff_millis(now_millis);
    now_millis >= cutoff && last_attempt_millis < cutoff
}
pub fn should_auto_refresh(now_millis: i64, last_attempt_millis: i64) -> bool {
    last_attempt_millis == 0 || is_due(now_millis, last_attempt_millis)
}

/// One startup recovery is allowed when the last attempt did not populate an empty cache.
pub fn should_recover_empty_timetable(
    has_cached_timetable: bool,
    last_attempt: i64,
    last_success: i64,
    already_attempted: bool,
) -> bool {
    !has_cached_timetable && !already_attempted && last_attempt > last_success
}

pub fn next_delay_millis(now_millis: i64, last_attempt_millis: i64) -> i64 {
    let cutoff = cutoff_millis(now_millis);
    if now_millis < cutoff { return cutoff.saturating_sub(now_millis); }
    if last_attempt_millis < cutoff { return 0; }
    cutoff.saturating_add(DAY_MS).saturating_sub(now_millis).max(0)
}

/// Compute today's calendar date in Vietnam (UTC+07:00, ISO format YYYY-MM-DD).
pub fn today_vietnam_ymd(now_millis: i64) -> String {
    let local_ms = now_millis.saturating_add(VIETNAM_OFFSET_MS);
    let epoch_days = local_ms.div_euclid(DAY_MS);
    chrono::NaiveDate::from_num_days_from_ce_opt((epoch_days + 719_163) as i32)
        .map(|d| d.format("%Y-%m-%d").to_string())
        .unwrap_or_else(|| "1970-01-01".to_string())
}
