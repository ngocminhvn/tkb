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
pub fn next_delay_millis(now_millis: i64, last_attempt_millis: i64) -> i64 {
    let cutoff = cutoff_millis(now_millis);
    if now_millis < cutoff { return cutoff.saturating_sub(now_millis); }
    if last_attempt_millis < cutoff { return 0; }
    cutoff.saturating_add(DAY_MS).saturating_sub(now_millis).max(0)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn six_thirty_in_vietnam_exactly() {
        // 1970-01-01 00:00 UTC is 07:00 in Vietnam.
        assert_eq!(cutoff_millis(0), -1_800_000);
        assert_eq!(next_delay_millis(-1_860_000, -DAY_MS), 60_000);
        assert!(!is_due(-1_800_001, -DAY_MS));
        assert!(is_due(-1_800_000, -DAY_MS));
        assert!(!is_due(-1_800_000, -1_800_000));
    }
    #[test]
    fn next_day_remains_eligible() {
        assert_eq!(next_delay_millis(0, -1_800_000), DAY_MS - 1_800_000);
        assert!(is_due(DAY_MS - 1_800_000, -1_800_000));
        assert_eq!(next_delay_millis(DAY_MS - 1_800_000, -1_800_000), 0);
    }
    #[test]
    fn previous_day_attempt_is_not_todays_attempt() {
        assert!(is_due(0, -DAY_MS));
        assert!(!is_due(0, 0));
    }
}
