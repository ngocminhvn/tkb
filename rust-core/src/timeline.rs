//! Timetable state, week/day transitions, and touch-drag boundaries.
//! Pure functions: no Android Context, persistence, or UI dependencies.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WeekSpan<'a> {
    pub tuan_hoc_ky: i32,
    pub start: &'a str,
    pub end: &'a str,
}

pub fn parse_week_spans(input: &str) -> Vec<WeekSpan<'_>> {
    if input.trim().is_empty() {
        return Vec::new();
    }
    input
        .split(';')
        .filter_map(|segment| {
            let mut parts = segment.split('|');
            let tuan = parts.next()?.trim().parse::<i32>().ok()?;
            let start = parts.next()?.trim();
            let end = parts.next()?.trim();
            Some(WeekSpan {
                tuan_hoc_ky: tuan,
                start,
                end,
            })
        })
        .collect()
}

pub fn resolve_selected_week(
    weeks: &[WeekSpan<'_>],
    manual_week: Option<i32>,
    today_ymd: &str,
) -> Option<i32> {
    if let Some(manual) = manual_week {
        if weeks.iter().any(|w| w.tuan_hoc_ky == manual) {
            return Some(manual);
        }
    }
    current_week_number(weeks, today_ymd)
}

pub fn calculate_extended_day_count(
    current_loaded_count: i32,
    last_loaded_day: &str,
    target_date: &str,
) -> i32 {
    if target_date <= last_loaded_day {
        return current_loaded_count;
    }
    use chrono::NaiveDate;
    let last_norm = crate::normalize_date(last_loaded_day);
    let target_norm = crate::normalize_date(target_date);
    if let (Ok(last), Ok(target)) = (
        NaiveDate::parse_from_str(&last_norm, "%Y-%m-%d"),
        NaiveDate::parse_from_str(&target_norm, "%Y-%m-%d"),
    ) {
        let delta = (target - last).num_days().max(0) as i32;
        return current_loaded_count.saturating_add(delta).saturating_add(7).min(1000);
    }
    // Fallback: step one day at a time (malformed date strings)
    let mut cursor = last_norm;
    let mut count = current_loaded_count;
    while cursor.as_str() < target_norm.as_str() && count < 1000 {
        let Some(next) = crate::move_calendar_day(&cursor, 1) else { break };
        if next <= cursor { break; }
        cursor = next;
        count += 1;
    }
    count.saturating_add(7).min(1000)
}

pub fn generate_date_window(start_ymd: &str, count: i32) -> Vec<String> {
    if count <= 0 {
        return Vec::new();
    }
    use chrono::NaiveDate;
    let iso = crate::normalize_date(start_ymd);
    let Some(mut date) = NaiveDate::parse_from_str(&iso, "%Y-%m-%d").ok() else {
        return Vec::new();
    };
    let mut dates = Vec::with_capacity(count as usize);
    for _ in 0..count {
        dates.push(date.format("%Y-%m-%d").to_string());
        let Some(next) = date.succ_opt() else { break };
        date = next;
    }
    dates
}

pub fn find_week_number(weeks: &[WeekSpan<'_>], date_ymd: &str) -> Option<i32> {
    if weeks.is_empty() {
        return None;
    }
    let date_norm = crate::normalize_date(date_ymd);
    weeks.iter().find_map(|w| {
        if date_norm >= crate::normalize_date(w.start)
            && date_norm <= crate::normalize_date(w.end)
        {
            Some(w.tuan_hoc_ky)
        } else {
            None
        }
    })
}

pub fn current_week_number(weeks: &[WeekSpan<'_>], today_ymd: &str) -> Option<i32> {
    if weeks.is_empty() {
        return None;
    }
    let today_norm = crate::normalize_date(today_ymd);
    // weeks arrive ORDER BY tuanHocKy ASC from the DB; no re-sort needed.
    // Normalize start/end once per span rather than allocating a sorted Vec.
    let found = weeks.iter().find(|w| {
        today_norm >= crate::normalize_date(w.start)
            && today_norm <= crate::normalize_date(w.end)
    });
    if let Some(w) = found {
        return Some(w.tuan_hoc_ky);
    }
    // Before first week → first week; after last week → last week.
    let first_start = crate::normalize_date(weeks[0].start);
    if today_norm < first_start {
        Some(weeks[0].tuan_hoc_ky)
    } else {
        weeks.last().map(|w| w.tuan_hoc_ky)
    }
}

pub fn adjacent_timetable_day(
    selected_ymd: &str,
    direction: i32,
    weeks: &[WeekSpan<'_>],
) -> Option<(String, i32)> {
    if direction == 0 || weeks.is_empty() {
        return None;
    }
    let next = crate::move_calendar_day(selected_ymd, direction)?;
    let week = weeks.iter().find(|w| {
        next >= crate::normalize_date(w.start) && next <= crate::normalize_date(w.end)
    })?;
    Some((next, week.tuan_hoc_ky))
}

pub fn elastic_edge_offset_px(distance_px: f32, threshold_px: f32, direction: i32) -> f32 {
    if threshold_px <= 0.0 || distance_px <= 0.0 || (direction != -1 && direction != 1) {
        return 0.0;
    }
    let distance = distance_px.min(threshold_px * 4.0);
    let stretched = 0.52 * distance / (1.0 + distance / (threshold_px * 1.2));
    if direction < 0 {
        stretched
    } else {
        -stretched
    }
}

pub fn confirmed_edge_week_direction(
    distance_px: f32,
    threshold_px: f32,
    direction: i32,
    has_adjacent_week: bool,
) -> Option<i32> {
    if (direction == 1 || direction == -1)
        && has_adjacent_week
        && threshold_px > 0.0
        && distance_px >= threshold_px
    {
        Some(direction)
    } else {
        None
    }
}

pub fn should_recreate_timetable_after_first_sync(
    had_cached_weeks_at_launch: bool,
    committed_weeks_count: usize,
    already_recreated: bool,
) -> bool {
    !had_cached_weeks_at_launch && committed_weeks_count > 0 && !already_recreated
}

pub fn should_prefer_just_synced_cache(
    observed_weeks_empty: bool,
    observed_lessons_empty: bool,
    has_just_synced: bool,
    just_synced_weeks_not_empty: bool,
    just_synced_lessons_not_empty: bool,
) -> bool {
    has_just_synced
        && ((observed_weeks_empty && just_synced_weeks_not_empty)
            || (observed_lessons_empty && just_synced_lessons_not_empty))
}

/// Stable order matches Kotlin String comparison and the input order for ties.
pub fn schedule_order(fields: &[String]) -> Vec<i32> {
    let (items, _) = fields.as_chunks::<2>();
    let mut indices: Vec<_> = (0..items.len()).collect();
    indices.sort_by(|&left, &right| {
        let [left_date, left_time] = &items[left];
        let [right_date, right_time] = &items[right];
        left_date.encode_utf16().cmp(right_date.encode_utf16())
            .then_with(|| left_time.encode_utf16().cmp(right_time.encode_utf16()))
    });
    indices.into_iter().map(|index| index as i32).collect()
}

/// The first row of each day, followed by the total row count.
pub fn day_row_offsets(counts: &[i32]) -> Option<Vec<i32>> {
    let mut offsets = vec![0];
    let mut total = 0_i32;
    for &count in counts {
        total = total.checked_add(count.max(1))?;
        offsets.push(total);
    }
    Some(offsets)
}

pub fn week_lesson_indices(lesson_weeks: &[i32], selected: i32) -> Vec<i32> {
    lesson_weeks.iter().enumerate().filter_map(|(index, &week)| {
        (week == selected).then_some(index as i32)
    }).collect()
}

pub fn should_append_days(last_visible: i32, row_count: i32, last_day: &str) -> bool {
    row_count > 0 && last_visible >= row_count.saturating_sub(6).max(0)
        && crate::move_calendar_day(last_day, 1).is_some()
}

/// TKB1Y contains sorted source indices, normalized dates and display times.
pub(crate) fn timeline_batch(
    lesson_dates: &[String],
    lesson_slots: &[i32],
    exam_fields: &[String],
    exam_slots: &[i32],
    period_fields: &[String],
    period_numbers: &[i32],
) -> Result<Vec<u8>, String> {
    use crate::portal::{binary_count, binary_header, binary_i32, binary_str};
    if lesson_slots.len() != lesson_dates.len() * 2
        || exam_fields.len() != exam_slots.len()
        || !exam_slots.len().is_multiple_of(2)
        || period_fields.len() != period_numbers.len() * 2
    {
        return Err("Invalid timeline batch shape".into());
    }

    let periods: std::collections::HashMap<_, _> = period_numbers.iter().copied()
        .zip(period_fields.as_chunks::<2>().0).collect();
    let mut rows = Vec::with_capacity(lesson_dates.len() + exam_slots.len() / 2);
    for (index, (date, &[start, count])) in lesson_dates.iter()
        .zip(lesson_slots.as_chunks::<2>().0).enumerate()
    {
        let end = start.wrapping_add(count).wrapping_sub(1);
        let label = crate::format_lesson_time(
            start,
            periods.get(&start).map(|times| times[0].as_str()),
            periods.get(&end).map_or("", |times| times[1].as_str()),
            false,
        );
        rows.push((index as i32, crate::normalize_date(date), format!("{start:0>2}"), label));
    }

    for (index, ([date, start], &[minutes, postponed])) in exam_fields.as_chunks::<2>().0
        .iter().zip(exam_slots.as_chunks::<2>().0).enumerate()
    {
        if postponed == 0 {
            let source = i32::try_from(index).map_err(|_| "Too many exams")?;
            let label = crate::exam_end_time(start, minutes).unwrap_or_default();
            rows.push((-1 - source, crate::normalize_date(date), start.clone(), label));
        }
    }
    rows.sort_by(|a, b| a.1.encode_utf16().cmp(b.1.encode_utf16())
        .then_with(|| a.2.encode_utf16().cmp(b.2.encode_utf16())));
    let mut out = binary_header(b'Y');
    binary_count(&mut out, rows.len())?;
    for (source, date, _, label) in rows {
        binary_i32(&mut out, source);
        binary_str(&mut out, &date)?;
        binary_str(&mut out, &label)?;
    }
    Ok(out)
}

pub fn move_week_index(weeks: &[i32], current: i32, delta: i32) -> Option<i32> {
    let index = weeks.iter().position(|&week| week == current)?;
    let target = (index as i64 + i64::from(delta)).clamp(0, (weeks.len() - 1) as i64);
    Some(target as i32)
}

pub fn can_start_exam_sync(show_exams: bool, force: bool, already_fetching: bool) -> bool {
    (show_exams || force) && !already_fetching
}

pub fn sync_failure_detail(issue: &str, exam_warning: &str, manual: bool) -> String {
    let mut detail = issue.to_owned();
    if crate::auth::has_session_value(exam_warning) {
        detail.push_str("\nLịch thi: ");
        detail.push_str(exam_warning);
    }
    if manual {
        detail.push_str(" Lịch đã lưu vẫn được giữ.");
    }
    detail
}
