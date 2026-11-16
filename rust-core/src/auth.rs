//! TNUT authorization policy. Keystore and HTTP remain Android-owned until migration tests.
const PORTAL_HOST: &str = "portal.tnut.edu.vn";
pub fn is_official_portal_origin(scheme: &str, hostname: &str) -> bool {
    scheme.eq_ignore_ascii_case("https") && hostname.eq_ignore_ascii_case(PORTAL_HOST)
}
pub fn can_reuse_rotated_token(current: &str, rejected: &str) -> bool {
    !current.is_empty() && current != rejected
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn tokens_never_go_to_foreign_origin() {
        assert!(is_official_portal_origin("https", "portal.tnut.edu.vn"));
        assert!(is_official_portal_origin("HTTPS", "PORTAL.TNUT.EDU.VN"));
        assert!(!is_official_portal_origin("http", "portal.tnut.edu.vn"));
        assert!(!is_official_portal_origin("https", "portal.tnut.edu.vn.evil.test"));
        assert!(!is_official_portal_origin("https", "evil.test"));
    }
    #[test]
    fn reuse_only_if_other_request_rotated_token() {
        assert!(can_reuse_rotated_token("rotated", "expired"));
        assert!(can_reuse_rotated_token("rotated", ""));
        assert!(!can_reuse_rotated_token("same", "same"));
        assert!(!can_reuse_rotated_token("", "expired"));
    }
}
