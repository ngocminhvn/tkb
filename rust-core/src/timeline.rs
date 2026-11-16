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

#[cfg(test)]
mod tests {
    use super::*;

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
}
