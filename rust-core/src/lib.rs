//! Platform-independent schedule operations for TKB SV.
//! No Android Context, network access, credentials, or UI dependencies.

/// Normalize portal date strings to YYYY-MM-DD. Unrecognized input is returned
/// unchanged so callers can decide how to handle malformed server data.
pub fn normalize_date(input: &str) -> String {
    let clean = input.trim().split([' ', 'T']).next().unwrap_or("");
    let separator = if clean.contains('/') { '/' } else { '-' };
    let parts: Vec<_> = clean.split(separator).collect();
    if parts.len() != 3 {
        return clean.to_string();
    }
    let (year, month, day) = if separator == '/' || parts[0].len() != 4 {
        (parts[2], parts[1], parts[0])
    } else {
        (parts[0], parts[1], parts[2])
    };
    format!("{year}-{month:0>2}-{day:0>2}")
}

/// End time of an exam, wrapping at midnight. Invalid input yields None.
pub fn exam_end_time(start: &str, duration_minutes: i32) -> Option<String> {
    if duration_minutes <= 0 {
        return None;
    }
    let mut parts = start.split(':');
    let hour: i32 = parts.next()?.trim().parse().ok()?;
    let minute: i32 = parts.next()?.trim().parse().ok()?;
    if parts.next().is_some() || !(0..24).contains(&hour) || !(0..60).contains(&minute) {
        return None;
    }
    let total = (hour * 60 + minute + duration_minutes).rem_euclid(1440);
    Some(format!("{:02}:{:02}", total / 60, total % 60))
}

/// Resolve a lesson time range using the actual period table.
pub fn lesson_time_range(periods: &[(i32, String, String)], start_period: i32, count: i32) -> String {
    let Some((_, start, _)) = periods.iter().find(|(n, _, _)| *n == start_period) else {
        return format!("Tiết {start_period}");
    };
    let end_period = start_period + count - 1;
    let end = periods.iter().find(|(n, _, _)| *n == end_period)
        .map(|(_, _, end)| end.as_str()).unwrap_or("");
    if end.is_empty() {
        start.clone()
    } else {
        format!("{start} – {end}")
    }
}


use chrono::{Days, NaiveDate};

#[cfg(target_os = "android")]
mod android;

/// Move one calendar date; semester constraints stay with the Android repository.
pub fn move_calendar_day(ymd: &str, direction: i32) -> Option<String> {
    if direction == 0 {
        return None;
    }
    let date = NaiveDate::parse_from_str(ymd, "%Y-%m-%d").ok()?;
    let next = if direction > 0 {
        date.checked_add_days(Days::new(1))?
    } else {
        date.checked_sub_days(Days::new(1))?
    };
    Some(next.format("%Y-%m-%d").to_string())
}

/// Seven consecutive ISO dates. Calendar arithmetic is independent of DST.
pub fn calendar_week_dates(start: &str) -> Vec<String> {
    let Some(first) = NaiveDate::parse_from_str(start, "%Y-%m-%d").ok() else {
        return Vec::new();
    };
    (0..7)
        .filter_map(|offset| first.checked_add_days(Days::new(offset)))
        .map(|date| date.format("%Y-%m-%d").to_string())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn portal_dates() {
        assert_eq!(normalize_date("7/11/2026"), "2026-11-07");
        assert_eq!(normalize_date("2026-11-7T07:00:00"), "2026-11-07");
        assert_eq!(normalize_date("07-11-2026 00:00:00"), "2026-11-07");
    }
    #[test]
    fn exam_times() {
        assert_eq!(exam_end_time("07:00", 90).as_deref(), Some("08:30"));
        assert_eq!(exam_end_time("23:45", 30).as_deref(), Some("00:15"));
        assert_eq!(exam_end_time("25:00", 30), None);
        assert_eq!(exam_end_time("07:00", 0), None);
    }
    #[test]
    fn day_navigation_and_week_dates() {
        assert_eq!(move_calendar_day("2026-11-08", 1).as_deref(), Some("2026-11-09"));
        assert_eq!(move_calendar_day("2026-11-09", -1).as_deref(), Some("2026-11-08"));
        assert_eq!(move_calendar_day("2026-11-09", 0), None);
        assert_eq!(calendar_week_dates("2026-12-28").last().map(String::as_str), Some("2027-01-03"));
        assert!(calendar_week_dates("bad-date").is_empty());
    }
    #[test]
    fn periods() {
        let p = vec![(1, "07:15".into(), "08:05".into()), (2, "08:10".into(), "09:00".into())];
        assert_eq!(lesson_time_range(&p, 1, 2), "07:15 – 09:00");
        assert_eq!(lesson_time_range(&p, 3, 1), "Tiết 3");
    }
}
