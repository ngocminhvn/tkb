//! JNI entry points for the TKB SV Android app. Data never leaves the process.
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jstring};
use jni::JNIEnv;

fn from_java(env: &mut JNIEnv<'_>, input: &JString<'_>) -> String {
    env.get_string(input).map(Into::into).unwrap_or_default()
}

fn into_java(env: &mut JNIEnv<'_>, value: String) -> jstring {
    env.new_string(value)
        .map(JString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_normalizeDateNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::normalize_date(&source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_examEndTimeNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
    minutes: jint,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(
        &mut env,
        crate::exam_end_time(&source, minutes).unwrap_or_default(),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_moveCalendarDayNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
    direction: jint,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(
        &mut env,
        crate::move_calendar_day(&source, direction).unwrap_or_default(),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_weekDatesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::calendar_week_dates(&source).join(","))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_dayRailNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    let text = crate::day_rail(&source)
        .map(|(day, label)| format!("{day}|{label}"))
        .unwrap_or_default();
    into_java(&mut env, text)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_mondayOfWeekNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::monday_of_week(&source).unwrap_or_default())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_monthDatesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    anchor: JString<'_>,
    month_offset: jint,
) -> jstring {
    let source = from_java(&mut env, &anchor);
    let text = crate::month_calendar_grid(&source, month_offset)
        .map(|(month, year, cells)| format!("{month}|{year}|{}", cells.join(",")))
        .unwrap_or_default();
    into_java(&mut env, text)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_dayLabelViNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::day_label_vi(&source).unwrap_or_default())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_formatDmyNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::format_dmy(&source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_formatDmNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::format_dm(&source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_addCalendarDaysNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
    delta_days: jint,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(
        &mut env,
        crate::add_calendar_days(&source, delta_days).unwrap_or_default(),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_parseHhMmMinutesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jint {
    let source = from_java(&mut env, &input);
    crate::parse_hhmm_minutes(&source).unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_formatBytesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    bytes: jni::sys::jlong,
) -> jstring {
    into_java(&mut env, crate::format_bytes(bytes))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_hasNextExamPageNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    page: jint,
    total_pages: jint,
    row_count: jint,
) -> jni::sys::jboolean {
    u8::from(crate::portal::exam_has_next(
        page.max(1) as usize,
        (total_pages > 0).then_some(total_pages as usize),
        row_count.max(0) as usize,
    ))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_normalizeNoteNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::portal::normalize_note(&source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_normalizeUsernameNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::portal::normalize_username(&source))
}

fn into_byte_array(env: &mut JNIEnv<'_>, result: Result<Vec<u8>, String>) -> jbyteArray {
    match result {
        Ok(bytes) => env.byte_array_from_slice(&bytes)
            .map(JByteArray::into_raw)
            .unwrap_or(std::ptr::null_mut()),
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            std::ptr::null_mut()
        }
    }
}

/// Exactly one JNI call per full TNUT timetable response; pass original
/// network bytes and receive binary DTOs, not a JSON round-trip.
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapTimetableBytesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    from_ymd: JString<'_>,
    source: JByteArray<'_>,
) -> jbyteArray {
    let from_ymd = from_java(&mut env, &from_ymd);
    let bytes = match env.convert_byte_array(&source) {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };
    into_byte_array(
        &mut env,
        crate::portal::map_timetable_wire(semester, &from_ymd, &bytes),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapExamBytesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    is_midterm: jni::sys::jboolean,
    source: JByteArray<'_>,
) -> jbyteArray {
    let bytes = match env.convert_byte_array(&source) {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };
    into_byte_array(&mut env, crate::portal::map_exam_wire(semester, is_midterm != 0, &bytes))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_isLogicalUnauthorizedNative(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    source: JByteArray<'_>,
) -> jni::sys::jboolean {
    let Ok(bytes) = env.convert_byte_array(&source) else {
        return 0;
    };
    u8::from(crate::portal::is_logical_unauthorized(&bytes))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_verifyUpdateManifestNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    current_version_code: jint,
    allowed_prefix: JString<'_>,
    source: JByteArray<'_>,
) -> jbyteArray {
    let prefix = from_java(&mut env, &allowed_prefix);
    let bytes = match env.convert_byte_array(&source) {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };
    match crate::portal::verify_update_manifest(current_version_code, &prefix, &bytes) {
        Ok(Some(packed)) => into_byte_array(&mut env, Ok(packed)),
        Ok(None) => into_byte_array(&mut env, Ok(Vec::new())),
        Err(message) => into_byte_array(&mut env, Err(message)),
    }
}


// Platform-independent policy exposed to Android; network and encrypted
// credential storage remain in Kotlin pending compatibility testing.
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_isOfficialPortalOriginNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    scheme: JString<'_>,
    hostname: JString<'_>,
) -> jni::sys::jboolean {
    let scheme = from_java(&mut env, &scheme);
    let hostname = from_java(&mut env, &hostname);
    u8::from(crate::auth::is_official_portal_origin(&scheme, &hostname))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_canReuseRotatedTokenNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    current: JString<'_>,
    rejected: JString<'_>,
) -> jni::sys::jboolean {
    let current = from_java(&mut env, &current);
    let rejected = from_java(&mut env, &rejected);
    u8::from(crate::auth::can_reuse_rotated_token(&current, &rejected))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_extractBearerTokenNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    auth_header: JString<'_>,
) -> jstring {
    let header = from_java(&mut env, &auth_header);
    let token = crate::auth::extract_bearer_token(&header).unwrap_or("").to_string();
    into_java(&mut env, token)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_shouldAuthenticateRetryNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    is_official: jboolean,
    path: JString<'_>,
    response_count: jint,
) -> jboolean {
    let path = from_java(&mut env, &path);
    u8::from(crate::auth::should_authenticate_retry(is_official != 0, &path, response_count))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_shouldFallbackToLoginNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    http_status: jint,
) -> jboolean {
    u8::from(crate::auth::should_fallback_to_login(http_status.max(0) as u16))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_vietnamAutoSyncCutoffNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    now_millis: jni::sys::jlong,
) -> jni::sys::jlong {
    crate::autosync::cutoff_millis(now_millis)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_isVietnamDailyAutoSyncDueNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    now_millis: jni::sys::jlong,
    last_attempt_millis: jni::sys::jlong,
) -> jni::sys::jboolean {
    u8::from(crate::autosync::is_due(now_millis, last_attempt_millis))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_nextVietnamAutoSyncDelayNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    now_millis: jni::sys::jlong,
    last_attempt_millis: jni::sys::jlong,
) -> jni::sys::jlong {
    crate::autosync::next_delay_millis(now_millis, last_attempt_millis)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_todayVietnamYmdNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    now_millis: jni::sys::jlong,
) -> jstring {
    let ymd = crate::autosync::today_vietnam_ymd(now_millis);
    into_java(&mut env, ymd)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_findWeekNumberNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    weeks_payload: JString<'_>,
    date_ymd: JString<'_>,
) -> jint {
    let payload = from_java(&mut env, &weeks_payload);
    let date = from_java(&mut env, &date_ymd);
    let spans = crate::timeline::parse_week_spans(&payload);
    crate::timeline::find_week_number(&spans, &date).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_currentWeekNumberNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    weeks_payload: JString<'_>,
    today_ymd: JString<'_>,
) -> jint {
    let payload = from_java(&mut env, &weeks_payload);
    let today = from_java(&mut env, &today_ymd);
    let spans = crate::timeline::parse_week_spans(&payload);
    crate::timeline::current_week_number(&spans, &today).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_adjacentTimetableDayNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    selected_ymd: JString<'_>,
    direction: jint,
    weeks_payload: JString<'_>,
) -> jstring {
    let selected = from_java(&mut env, &selected_ymd);
    let payload = from_java(&mut env, &weeks_payload);
    let spans = crate::timeline::parse_week_spans(&payload);
    let result = crate::timeline::adjacent_timetable_day(&selected, direction, &spans)
        .map(|(date, week)| format!("{date}:{week}"))
        .unwrap_or_default();
    into_java(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_elasticEdgeOffsetPxNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    distance_px: jfloat,
    threshold_px: jfloat,
    direction: jint,
) -> jfloat {
    crate::timeline::elastic_edge_offset_px(distance_px, threshold_px, direction)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_confirmedEdgeWeekDirectionNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    distance_px: jfloat,
    threshold_px: jfloat,
    direction: jint,
    has_adjacent_week: jboolean,
) -> jint {
    crate::timeline::confirmed_edge_week_direction(
        distance_px,
        threshold_px,
        direction,
        has_adjacent_week != 0,
    )
    .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_shouldRecreateTimetableAfterFirstSyncNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    had_cached_weeks: jboolean,
    committed_weeks_count: jint,
    already_recreated: jboolean,
) -> jboolean {
    u8::from(crate::timeline::should_recreate_timetable_after_first_sync(
        had_cached_weeks != 0,
        committed_weeks_count.max(0) as usize,
        already_recreated != 0,
    ))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_shouldPreferJustSyncedCacheNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    observed_weeks_empty: jboolean,
    observed_lessons_empty: jboolean,
    has_just_synced: jboolean,
    just_synced_weeks_not_empty: jboolean,
    just_synced_lessons_not_empty: jboolean,
) -> jboolean {
    u8::from(crate::timeline::should_prefer_just_synced_cache(
        observed_weeks_empty != 0,
        observed_lessons_empty != 0,
        has_just_synced != 0,
        just_synced_weeks_not_empty != 0,
        just_synced_lessons_not_empty != 0,
    ))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_resolveSelectedWeekNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    weeks_payload: JString<'_>,
    manual_week: jint,
    today_ymd: JString<'_>,
) -> jint {
    let payload = from_java(&mut env, &weeks_payload);
    let today = from_java(&mut env, &today_ymd);
    let spans = crate::timeline::parse_week_spans(&payload);
    let manual = (manual_week > 0).then_some(manual_week);
    crate::timeline::resolve_selected_week(&spans, manual, &today).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_calculateExtendedDayCountNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    current_count: jint,
    last_loaded_day: JString<'_>,
    target_date: JString<'_>,
) -> jint {
    let last = from_java(&mut env, &last_loaded_day);
    let target = from_java(&mut env, &target_date);
    crate::timeline::calculate_extended_day_count(current_count, &last, &target)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_generateDateWindowNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    start_ymd: JString<'_>,
    count: jint,
) -> jstring {
    let start = from_java(&mut env, &start_ymd);
    let dates = crate::timeline::generate_date_window(&start, count);
    into_java(&mut env, dates.join(","))
}
