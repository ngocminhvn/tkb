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
    let mut cursor = last_loaded_day.to_string();
    let mut count = current_loaded_count;
    while cursor.as_str() < target_date && count < 1000 {
        let Some(next) = crate::move_calendar_day(&cursor, 1) else {
            break;
        };
        if next <= cursor {
            break;
        }
        cursor = next;
        count += 1;
    }
    count.saturating_add(7).min(1000)
}

pub fn generate_date_window(start_ymd: &str, count: i32) -> Vec<String> {
    if count <= 0 {
        return Vec::new();
    }
    let mut dates = Vec::with_capacity(count as usize);
    let mut current = crate::normalize_date(start_ymd);
    dates.push(current.clone());
    for _ in 1..count {
        let Some(next) = crate::move_calendar_day(&current, 1) else {
            break;
        };
        if next <= current {
            break;
        }
        current = next;
        dates.push(current.clone());
    }
    dates
}

pub fn find_week_number(weeks: &[WeekSpan<'_>], date_ymd: &str) -> Option<i32> {
    if weeks.is_empty() {
        return None;
    }
    let date_norm = crate::normalize_date(date_ymd);
    weeks.iter().find_map(|w| {
        let start = crate::normalize_date(w.start);
        let end = crate::normalize_date(w.end);
        if date_norm >= start && date_norm <= end {
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
    let mut normalized: Vec<(String, String, i32)> = weeks
        .iter()
        .map(|w| {
            (
                crate::normalize_date(w.start),
                crate::normalize_date(w.end),
                w.tuan_hoc_ky,
            )
        })
        .collect();
    normalized.sort_by(|a, b| a.0.cmp(&b.0));

    normalized
        .iter()
        .find(|(start, end, _)| today_norm >= *start && today_norm <= *end)
        .map(|(_, _, tuan)| *tuan)
        .or_else(|| {
            if today_norm < normalized[0].0 {
                Some(normalized[0].2)
            } else {
                Some(normalized.last()?.2)
            }
        })
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
        let start = crate::normalize_date(w.start);
        let end = crate::normalize_date(w.end);
        next >= start && next <= end
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_stable_schedule_order_and_empty_day_rows() {
        let fields = ["2026-11-17", "09:00", "2026-11-16", "10:00",
            "2026-11-16", "08:00", "2026-11-16", "08:00"].map(str::to_owned);
        assert_eq!(schedule_order(&fields), [2, 3, 1, 0]);
        assert_eq!(day_row_offsets(&[0, 3, 1]), Some(vec![0, 1, 4, 5]));
        assert_eq!(day_row_offsets(&[]), Some(vec![0]));
        assert_eq!(day_row_offsets(&[i32::MAX, 1]), None);
    }

    #[test]
    fn selects_week_lessons_and_prefetches_at_existing_boundary() {
        assert_eq!(week_lesson_indices(&[3, 2, 3, 4], 3), [0, 2]);
        assert!(!should_append_days(13, 20, "2026-11-16"));
        assert!(should_append_days(14, 20, "2026-11-16"));
        assert!(!should_append_days(0, 0, "2026-11-16"));
    }

    #[test]
    fn parses_compact_week_spans() {
        let payload = "1|2026-09-01|2026-09-07;2|2026-09-08 00:00:00|2026-09-14";
        let spans = parse_week_spans(payload);
        assert_eq!(spans.len(), 2);
        assert_eq!(spans[0].tuan_hoc_ky, 1);
        assert_eq!(spans[0].start, "2026-09-01");
        assert_eq!(spans[1].tuan_hoc_ky, 2);
        assert_eq!(spans[1].start, "2026-09-08 00:00:00");
    }

    #[test]
    fn finds_exact_week_for_date() {
        let weeks = [
            WeekSpan {
                tuan_hoc_ky: 1,
                start: "2026-09-01",
                end: "2026-09-07",
            },
            WeekSpan {
                tuan_hoc_ky: 2,
                start: "2026-09-08",
                end: "2026-09-14",
            },
        ];
        assert_eq!(find_week_number(&weeks, "2026-09-05"), Some(1));
        assert_eq!(find_week_number(&weeks, "2026-09-08"), Some(2));
        assert_eq!(find_week_number(&weeks, "2026-08-31"), None);
        assert_eq!(find_week_number(&weeks, "2026-09-15"), None);
    }

    #[test]
    fn selects_current_week_correctly() {
        let weeks = [
            WeekSpan {
                tuan_hoc_ky: 2,
                start: "2026-09-08",
                end: "2026-09-14",
            },
            WeekSpan {
                tuan_hoc_ky: 1,
                start: "2026-09-01",
                end: "2026-09-07",
            },
        ];
        // Middle of week 1
        assert_eq!(current_week_number(&weeks, "2026-09-04"), Some(1));
        // Boundary of week 2
        assert_eq!(current_week_number(&weeks, "2026-09-14"), Some(2));
        // Before semester starts -> picks first week
        assert_eq!(current_week_number(&weeks, "2026-08-20"), Some(1));
        // After semester ends -> picks last week
        assert_eq!(current_week_number(&weeks, "2026-10-01"), Some(2));
    }

    #[test]
    fn moves_to_adjacent_timetable_day() {
        let weeks = [
            WeekSpan {
                tuan_hoc_ky: 1,
                start: "2026-09-01",
                end: "2026-09-07",
            },
            WeekSpan {
                tuan_hoc_ky: 2,
                start: "2026-09-08",
                end: "2026-09-14",
            },
        ];
        // Step forward within week 1
        assert_eq!(
            adjacent_timetable_day("2026-09-01", 1, &weeks),
            Some(("2026-09-02".to_string(), 1))
        );
        // Step forward across week boundary from Sunday into Monday
        assert_eq!(
            adjacent_timetable_day("2026-09-07", 1, &weeks),
            Some(("2026-09-08".to_string(), 2))
        );
        // Step backward across week boundary from Monday into Sunday
        assert_eq!(
            adjacent_timetable_day("2026-09-08", -1, &weeks),
            Some(("2026-09-07".to_string(), 1))
        );
        // Out of semester bounds
        assert_eq!(adjacent_timetable_day("2026-09-01", -1, &weeks), None);
        assert_eq!(adjacent_timetable_day("2026-09-14", 1, &weeks), None);
        // Direction 0
        assert_eq!(adjacent_timetable_day("2026-09-05", 0, &weeks), None);
    }

    #[test]
    fn calculates_elastic_edge_physics() {
        assert_eq!(elastic_edge_offset_px(0.0, 100.0, 1), 0.0);
        assert_eq!(elastic_edge_offset_px(50.0, 0.0, 1), 0.0);
        assert_eq!(elastic_edge_offset_px(50.0, 100.0, 0), 0.0);

        let offset_neg = elastic_edge_offset_px(50.0, 100.0, -1);
        let offset_pos = elastic_edge_offset_px(50.0, 100.0, 1);
        assert!(offset_neg > 0.0);
        assert_eq!(offset_neg, -offset_pos);
    }

    #[test]
    fn confirms_edge_week_direction_threshold() {
        assert_eq!(confirmed_edge_week_direction(100.0, 100.0, 1, true), Some(1));
        assert_eq!(confirmed_edge_week_direction(99.0, 100.0, 1, true), None);
        assert_eq!(confirmed_edge_week_direction(150.0, 100.0, -1, false), None);
    }

    #[test]
    fn recreates_timetable_first_sync_guard() {
        assert!(should_recreate_timetable_after_first_sync(false, 5, false));
        assert!(!should_recreate_timetable_after_first_sync(true, 5, false));
        assert!(!should_recreate_timetable_after_first_sync(false, 0, false));
        assert!(!should_recreate_timetable_after_first_sync(false, 5, true));
    }

    #[test]
    fn prefers_just_synced_cache_when_observed_is_empty() {
        assert!(should_prefer_just_synced_cache(true, false, true, true, false));
        assert!(should_prefer_just_synced_cache(false, true, true, false, true));
        assert!(!should_prefer_just_synced_cache(false, false, true, true, true));
        assert!(!should_prefer_just_synced_cache(true, true, false, true, true));
    }

    #[test]
    fn resolves_selected_week_with_manual_override() {
        let weeks = [
            WeekSpan { tuan_hoc_ky: 1, start: "2026-09-01", end: "2026-09-07" },
            WeekSpan { tuan_hoc_ky: 2, start: "2026-09-08", end: "2026-09-14" },
        ];
        // Manual week 2 exists
        assert_eq!(resolve_selected_week(&weeks, Some(2), "2026-09-04"), Some(2));
        // Non-existent manual week falls back to current week (1)
        assert_eq!(resolve_selected_week(&weeks, Some(99), "2026-09-04"), Some(1));
        // No manual week falls back to current week (1)
        assert_eq!(resolve_selected_week(&weeks, None, "2026-09-04"), Some(1));
    }

    #[test]
    fn calculates_extended_day_count_for_future_navigation() {
        // Target before or equal to last loaded day returns current count
        assert_eq!(calculate_extended_day_count(35, "2026-09-30", "2026-09-20"), 35);
        assert_eq!(calculate_extended_day_count(35, "2026-09-30", "2026-09-30"), 35);
        // Target 2 days after last loaded day: 35 + 2 + 7 = 44
        assert_eq!(calculate_extended_day_count(35, "2026-09-28", "2026-09-30"), 44);
    }

    #[test]
    fn generates_date_window_continuously() {
        let window = generate_date_window("2026-09-29", 4);
        assert_eq!(
            window,
            vec![
                "2026-09-29".to_string(),
                "2026-09-30".to_string(),
                "2026-10-01".to_string(),
                "2026-10-02".to_string(),
            ]
        );
        assert!(generate_date_window("2026-09-29", 0).is_empty());
    }
}
