//! Platform-independent schedule operations for TKB SV.
//! No Android Context, network access, credentials, or UI dependencies.

/// Fast check for already-normalized ISO date strings (`YYYY-MM-DD`).
#[inline]
fn valid_iso_date(value: &str) -> bool {
    let bytes = value.as_bytes();
    bytes.len() == 10
        && bytes[4] == b'-'
        && bytes[7] == b'-'
        && bytes[0..4].iter().all(u8::is_ascii_digit)
        && bytes[5..7].iter().all(u8::is_ascii_digit)
        && bytes[8..10].iter().all(u8::is_ascii_digit)
}

/// Normalize portal date strings to YYYY-MM-DD. Unrecognized input is returned
/// unchanged so callers can decide how to handle malformed server data.
pub fn normalize_date(input: &str) -> String {
    let trimmed = input.trim();
    if valid_iso_date(trimmed) {
        return trimmed.to_string();
    }
    let clean = trimmed.split([' ', 'T']).next().unwrap_or("");
    if valid_iso_date(clean) {
        return clean.to_string();
    }
    let separator = if clean.contains('/') { '/' } else { '-' };
    let mut parts = clean.split(separator);
    let (Some(p0), Some(p1), Some(p2), None) =
        (parts.next(), parts.next(), parts.next(), parts.next())
    else {
        return clean.to_string();
    };
    let (year, month, day) = if separator == '/' || p0.len() != 4 {
        (p2, p1, p0)
    } else {
        (p0, p1, p2)
    };
    format!("{year}-{month:0>2}-{day:0>2}")
}

/// Format any portal or ISO date string as `dd/MM/yyyy` for Vietnamese UI display.
pub fn format_dmy(input: &str) -> String {
    let iso = normalize_date(input);
    if valid_iso_date(&iso) {
        format!("{}/{}/{}", &iso[8..10], &iso[5..7], &iso[0..4])
    } else {
        input.trim().to_string()
    }
}

/// Format any portal or ISO date string as `dd/MM` for compact UI badges.
pub fn format_dm(input: &str) -> String {
    let iso = normalize_date(input);
    if valid_iso_date(&iso) {
        format!("{}/{}", &iso[8..10], &iso[5..7])
    } else {
        input.trim().to_string()
    }
}

/// Parse an `HH:mm` clock string into minutes since midnight (`0..1440`).
pub fn parse_hhmm_minutes(clock: &str) -> Option<i32> {
    let mut parts = clock.trim().split(':');
    let hour: i32 = parts.next()?.trim().parse().ok()?;
    let minute: i32 = parts.next()?.trim().parse().ok()?;
    if parts.next().is_some() || !(0..24).contains(&hour) || !(0..60).contains(&minute) {
        return None;
    }
    Some(hour * 60 + minute)
}

/// End time of an exam, wrapping at midnight. Invalid input yields None.
pub fn exam_end_time(start: &str, duration_minutes: i32) -> Option<String> {
    if duration_minutes <= 0 {
        return None;
    }
    let start_mins = parse_hhmm_minutes(start)?;
    let total = (start_mins + duration_minutes).rem_euclid(1440);
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


use chrono::{Datelike, Days, Months, NaiveDate, Weekday};

pub mod portal;
mod android;

/// Move one calendar date; semester constraints stay with the Android repository.
pub fn move_calendar_day(ymd: &str, direction: i32) -> Option<String> {
    if direction == 0 {
        return None;
    }
    let iso = normalize_date(ymd);
    let date = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let next = if direction > 0 {
        date.checked_add_days(Days::new(1))?
    } else {
        date.checked_sub_days(Days::new(1))?
    };
    Some(next.format("%Y-%m-%d").to_string())
}

/// Add or subtract an arbitrary number of calendar days from a date.
pub fn add_calendar_days(ymd: &str, delta_days: i32) -> Option<String> {
    let iso = normalize_date(ymd);
    let date = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let shifted = if delta_days >= 0 {
        date.checked_add_days(Days::new(delta_days as u64))?
    } else {
        date.checked_sub_days(Days::new(delta_days.unsigned_abs() as u64))?
    };
    Some(shifted.format("%Y-%m-%d").to_string())
}

/// Monday (`YYYY-MM-DD`) of the ISO week containing `ymd`.
pub fn monday_of_week(ymd: &str) -> Option<String> {
    let iso = normalize_date(ymd);
    let date = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let days_from_monday = date.weekday().num_days_from_monday() as u64;
    let monday = date.checked_sub_days(Days::new(days_from_monday))?;
    Some(monday.format("%Y-%m-%d").to_string())
}

/// Seven consecutive ISO dates. Calendar arithmetic is independent of DST.
pub fn calendar_week_dates(start: &str) -> Vec<String> {
    let iso = normalize_date(start);
    let Some(first) = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok() else {
        return Vec::new();
    };
    (0..7)
        .filter_map(|offset| first.checked_add_days(Days::new(offset)))
        .map(|date| date.format("%Y-%m-%d").to_string())
        .collect()
}

/// Day-of-month and Vietnamese weekday label ("T2".."T7", "CN") for a date rail.
/// Replaces two Java SimpleDateFormat constructions per list item.
pub fn day_rail(ymd: &str) -> Option<(u32, &'static str)> {
    let iso = normalize_date(ymd);
    let date = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let label = match date.weekday() {
        Weekday::Mon => "T2",
        Weekday::Tue => "T3",
        Weekday::Wed => "T4",
        Weekday::Thu => "T5",
        Weekday::Fri => "T6",
        Weekday::Sat => "T7",
        Weekday::Sun => "CN",
    };
    Some((date.day(), label))
}

/// Full Vietnamese weekday and date title, e.g. `"Thứ 2, 09/11"` or `"Chủ nhật, 08/11"`.
pub fn day_label_vi(ymd: &str) -> Option<String> {
    let iso = normalize_date(ymd);
    let date = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let weekday = match date.weekday() {
        Weekday::Mon => "Thứ 2",
        Weekday::Tue => "Thứ 3",
        Weekday::Wed => "Thứ 4",
        Weekday::Thu => "Thứ 5",
        Weekday::Fri => "Thứ 6",
        Weekday::Sat => "Thứ 7",
        Weekday::Sun => "Chủ nhật",
    };
    Some(format!("{weekday}, {:02}/{:02}", date.day(), date.month()))
}

/// Build the month calendar grid (Monday-aligned rows of 7 cells) for `anchor_ymd`
/// shifted by `month_offset` months. Returns `(month, year, cells)` where empty
/// leading/trailing cells are empty strings and active days are `YYYY-MM-DD`.
pub fn month_calendar_grid(anchor_ymd: &str, month_offset: i32) -> Option<(u32, i32, Vec<String>)> {
    let iso = normalize_date(anchor_ymd);
    let anchor = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok()?;
    let first_of_anchor = anchor.with_day(1)?;
    let target_first = if month_offset >= 0 {
        first_of_anchor.checked_add_months(Months::new(month_offset as u32))?
    } else {
        first_of_anchor.checked_sub_months(Months::new(month_offset.unsigned_abs()))?
    };
    let year = target_first.year();
    let month = target_first.month();
    let next_month_first = target_first.checked_add_months(Months::new(1))?;
    let days_in_month = next_month_first
        .signed_duration_since(target_first)
        .num_days() as u32;
    let leading_blanks = target_first.weekday().num_days_from_monday() as usize;
    let total_cells = (leading_blanks + days_in_month as usize).div_ceil(7) * 7;
    let mut cells = Vec::with_capacity(total_cells);
    for _ in 0..leading_blanks {
        cells.push(String::new());
    }
    for day in 1..=days_in_month {
        cells.push(format!("{year:04}-{month:02}-{day:02}"));
    }
    while cells.len() < total_cells {
        cells.push(String::new());
    }
    Some((month, year, cells))
}

/// Determine whether all lessons and exams on a day have finished at `now_minutes`
/// (`hour * 60 + minute`), used by the home screen widget to auto-advance to tomorrow.
pub fn is_day_schedule_finished(
    last_lesson_end_hhmm: Option<&str>,
    fallback_period_end_minutes: Option<i32>,
    exams: &[(&str, i32)],
    now_minutes: i32,
) -> bool {
    let last_lesson_end = last_lesson_end_hhmm
        .and_then(parse_hhmm_minutes)
        .or(fallback_period_end_minutes)
        .unwrap_or(0);
    let last_exam_end = exams
        .iter()
        .filter_map(|(start, duration)| {
            let start_min = parse_hhmm_minutes(start)?;
            Some(start_min + (*duration).max(60))
        })
        .max()
        .unwrap_or(0);
    let last_end = last_lesson_end.max(last_exam_end);
    last_end > 0 && now_minutes >= last_end
}

/// Format byte counts as human-readable `KB` or `MB` strings (US decimal point).
pub fn format_bytes(bytes: i64) -> String {
    let clamped = bytes.max(0) as f64;
    let mb = clamped / (1024.0 * 1024.0);
    if mb >= 1.0 {
        format!("{mb:.1} MB")
    } else {
        let kb = (clamped / 1024.0).max(1.0);
        format!("{kb:.0} KB")
    }
}
