//! TNUT authorization policy. Keystore and HTTP remain Android-owned until migration tests.
const PORTAL_HOST: &str = "portal.tnut.edu.vn";

pub fn is_official_portal_origin(scheme: &str, hostname: &str) -> bool {
    scheme.eq_ignore_ascii_case("https") && hostname.eq_ignore_ascii_case(PORTAL_HOST)
}

pub fn can_reuse_rotated_token(current: &str, rejected: &str) -> bool {
    !current.is_empty() && current != rejected
}

/// Strip 'Bearer ' prefix and whitespace; return Some(&str) only if token is non-empty.
pub fn extract_bearer_token(header_value: &str) -> Option<&str> {
    let trimmed = header_value.trim();
    if let Some(token) = trimmed.strip_prefix("Bearer ") {
        let clean = token.trim();
        if !clean.is_empty() {
            return Some(clean);
        }
    }
    None
}

/// Determine whether an HTTP 401 response is eligible for automatic session renewal.
/// Login endpoint requests and requests that have already retried must never loop.
pub fn should_authenticate_retry(is_official: bool, path: &str, response_count: i32) -> bool {
    if !is_official || response_count >= 2 {
        return false;
    }
    let clean = path.trim().trim_end_matches('/');
    !clean.ends_with("auth/login")
}

/// When token refresh returns an HTTP status code, determine if credentials-based
/// login fallback should be attempted. A server-side 5xx outage is not a reason to try password.
pub fn should_fallback_to_login(http_status: u16) -> bool {
    (400..500).contains(&http_status)
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

    #[test]
    fn extracts_bearer_token_cleanly() {
        assert_eq!(extract_bearer_token("Bearer abc123xyz"), Some("abc123xyz"));
        assert_eq!(extract_bearer_token("  Bearer   token_data  "), Some("token_data"));
        assert_eq!(extract_bearer_token("Bearer "), None);
        assert_eq!(extract_bearer_token("Basic abc"), None);
        assert_eq!(extract_bearer_token(""), None);
    }

    #[test]
    fn controls_auth_retry_eligibility() {
        assert!(should_authenticate_retry(true, "/api/tkb/get-all", 1));
        // Max 1 retry
        assert!(!should_authenticate_retry(true, "/api/tkb/get-all", 2));
        assert!(!should_authenticate_retry(true, "/api/tkb/get-all", 3));
        // Foreign origin
        assert!(!should_authenticate_retry(false, "/api/tkb/get-all", 1));
        // Login endpoint never retries recursively
        assert!(!should_authenticate_retry(true, "/api/auth/login", 1));
        assert!(!should_authenticate_retry(true, "/api/auth/login/", 1));
    }

    #[test]
    fn handles_login_fallback_policy() {
        assert!(should_fallback_to_login(400));
        assert!(should_fallback_to_login(401));
        assert!(should_fallback_to_login(403));
        // 5xx server outages must not compromise or spam credentials
        assert!(!should_fallback_to_login(500));
        assert!(!should_fallback_to_login(502));
        assert!(!should_fallback_to_login(503));
        assert!(!should_fallback_to_login(200));
    }
}
