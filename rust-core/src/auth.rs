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

pub(crate) fn has_session_value(value: &str) -> bool {
    // Kotlin Char.isWhitespace uses Java isWhitespace or isSpaceChar.
    value.chars().any(|c| {
        !((c.is_whitespace() && c != '\u{85}') || ('\u{1c}'..='\u{1f}').contains(&c))
    })
}

pub fn account_changed(previous: Option<&str>, username: &str) -> bool {
    previous.is_some_and(|previous| previous != username)
}

pub fn rotation_fields(refresh_token: &str, idpc: &str) -> i32 {
    i32::from(has_session_value(refresh_token)) | (i32::from(has_session_value(idpc)) << 1)
}

#[derive(Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum RenewalAction {
    Stop,
    UseRefresh,
    FallbackLogin,
}

pub fn renewal_action(http_status: i32, io_failure: bool, access: &str) -> RenewalAction {
    if io_failure || (http_status != 0 && !u16::try_from(http_status).is_ok_and(should_fallback_to_login)) {
        RenewalAction::Stop
    } else if has_session_value(access) {
        RenewalAction::UseRefresh
    } else {
        RenewalAction::FallbackLogin
    }
}

#[derive(Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum RenewalStart {
    ReuseCurrent,
    Refresh,
    Login,
}

pub fn renewal_start(current: &str, rejected: &str, refresh: &str) -> RenewalStart {
    if has_session_value(current) && can_reuse_rotated_token(current, rejected) {
        RenewalStart::ReuseCurrent
    } else if has_session_value(refresh) {
        RenewalStart::Refresh
    } else {
        RenewalStart::Login
    }
}

#[derive(Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum RequestKind {
    OtherOrigin,
    Login,
    Session,
}

pub fn request_kind(scheme: &str, hostname: &str, path: &str) -> RequestKind {
    if !is_official_portal_origin(scheme, hostname) {
        RequestKind::OtherOrigin
    } else if path.ends_with("auth/login") {
        RequestKind::Login
    } else {
        RequestKind::Session
    }
}
