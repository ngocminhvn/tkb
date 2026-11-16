//! JNI entry points for the TKB SV Android app. Data never leaves the process.
use jni::objects::{JByteArray, JIntArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jintArray, jlong, jobjectArray, jstring};
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


#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_timetableRequestNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
) -> jbyteArray {
    into_byte_array(&mut env, Ok(crate::portal::timetable_request(semester)))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_examRequestNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    midterm: jboolean,
    page: jint,
) -> jbyteArray {
    if page < 1 {
        return into_byte_array(&mut env, Err("Trang lịch thi phải >= 1".into()));
    }
    into_byte_array(
        &mut env,
        Ok(crate::portal::exam_request(semester, midterm != 0, page as usize)),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_nextExamPageNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    page: jint,
    total_pages: jint,
    row_count: jint,
) -> jint {
    match crate::portal::next_exam_page(
        page.max(1) as usize,
        usize::try_from(total_pages).ok(),
        row_count.max(0) as usize,
    ) {
        Ok(next) => next.unwrap_or(0) as jint,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_readReleaseUpdateNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    current_version: jlong,
    allowed_prefix: JString<'_>,
    source: JByteArray<'_>,
) -> jbyteArray {
    let prefix = from_java(&mut env, &allowed_prefix);
    let Ok(bytes) = env.convert_byte_array(&source) else {
        return std::ptr::null_mut();
    };
    into_byte_array(
        &mut env,
        crate::portal::read_release_update(current_version, &prefix, &bytes),
    )
}

fn read_strings(env: &mut JNIEnv<'_>, input: &JObjectArray<'_>) -> jni::errors::Result<Vec<String>> {
    let len = env.get_array_length(input)?;
    let mut strings = Vec::with_capacity(len as usize);
    for index in 0..len {
        let element = env.get_object_array_element(input, index)?;
        let local = env.auto_local(JString::from(element));
        strings.push(env.get_string(&local)?.into());
    }
    Ok(strings)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_uniqueExamIndicesNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    flat_keys: JObjectArray<'_>,
) -> jintArray {
    let Ok(fields) = read_strings(&mut env, &flat_keys) else {
        return std::ptr::null_mut();
    };
    let (keys, remainder) = fields.as_chunks::<6>();
    if !remainder.is_empty() {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Invalid exam identity");
        return std::ptr::null_mut();
    }
    let indices = crate::portal::first_exam_indices(keys);
    let Ok(array) = env.new_int_array(indices.len() as jint) else {
        return std::ptr::null_mut();
    };
    if env.set_int_array_region(&array, 0, &indices).is_err() {
        return std::ptr::null_mut();
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_widgetDayFinishedNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    now_minutes: jint,
    lesson_ends: JObjectArray<'_>,
    exam_starts: JObjectArray<'_>,
    exam_durations: JIntArray<'_>,
) -> jboolean {
    let (Ok(lesson_ends), Ok(exam_starts)) = (
        read_strings(&mut env, &lesson_ends),
        read_strings(&mut env, &exam_starts),
    ) else {
        return 0;
    };
    let Ok(count) = env.get_array_length(&exam_durations) else {
        return 0;
    };
    if count as usize != exam_starts.len() {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Invalid widget exam schedule");
        return 0;
    }
    let mut durations = vec![0; count as usize];
    if env.get_int_array_region(&exam_durations, 0, &mut durations).is_err() {
        return 0;
    }
    u8::from(crate::widget_day_finished(
        now_minutes,
        lesson_ends.iter().map(String::as_str),
        exam_starts.iter().map(String::as_str).zip(durations),
    ))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_formatLessonTimeNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    period: jint,
    start: JString<'_>,
    end: JString<'_>,
    compact: jboolean,
) -> jstring {
    let start = (!start.is_null()).then(|| from_java(&mut env, &start));
    let end = from_java(&mut env, &end);
    into_java(&mut env, crate::format_lesson_time(period, start.as_deref(), &end, compact != 0))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_localYmdNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    epoch_ms: jlong,
    offset_ms: jint,
) -> jstring {
    into_java(&mut env, crate::local_ymd(epoch_ms, offset_ms).unwrap_or_default())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapAvatarNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    source: JByteArray<'_>,
) -> jbyteArray {
    let Ok(bytes) = env.convert_byte_array(&source) else {
        return std::ptr::null_mut();
    };
    into_byte_array(&mut env, crate::portal::map_avatar_wire(&bytes))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_reminderMessageNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    fields: JObjectArray<'_>,
) -> jobjectArray {
    let Ok(fields) = read_strings(&mut env, &fields) else {
        return std::ptr::null_mut();
    };
    if fields.len() % 2 != 0 {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Invalid reminder rows");
        return std::ptr::null_mut();
    }
    let mut fields = fields.into_iter();
    let rows = std::iter::from_fn(|| Some((fields.next()?, fields.next()?))).collect();
    let (summary, message) = crate::reminder_message(rows);
    let Ok(output) = env.new_object_array(2, "java/lang/String", JObject::null()) else {
        return std::ptr::null_mut();
    };
    for (index, value) in [(0, summary), (1, message)] {
        let Ok(value) = env.new_string(value) else {
            return std::ptr::null_mut();
        };
        if env.set_object_array_element(&output, index, value).is_err() {
            return std::ptr::null_mut();
        }
    }
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_unpackUpdateNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    package: JString<'_>,
    destination: JString<'_>,
    checksum: JString<'_>,
    compressed: jboolean,
) -> jboolean {
    let package = from_java(&mut env, &package);
    let destination = from_java(&mut env, &destination);
    let checksum = from_java(&mut env, &checksum);
    match crate::update::unpack(
        std::path::Path::new(&package),
        std::path::Path::new(&destination),
        &checksum,
        compressed != 0,
    ) {
        Ok(verified) => u8::from(verified),
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapLoginNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    source: JByteArray<'_>,
) -> jbyteArray {
    let Ok(bytes) = env.convert_byte_array(&source) else {
        return std::ptr::null_mut();
    };
    into_byte_array(&mut env, crate::portal::map_login_wire(&bytes))
}

fn int_array<'local>(env: &JNIEnv<'local>, values: &[i32]) -> jni::errors::Result<JIntArray<'local>> {
    let output = env.new_int_array(values.len() as jint)?;
    env.set_int_array_region(&output, 0, values)?;
    Ok(output)
}

fn read_ints(env: &JNIEnv<'_>, input: JIntArray<'_>) -> jni::errors::Result<Vec<i32>> {
    let mut values = vec![0; env.get_array_length(&input)? as usize];
    env.get_int_array_region(&input, 0, &mut values)?;
    Ok(values)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_scheduleOrderNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    fields: JObjectArray<'_>,
) -> jintArray {
    let Ok(fields) = read_strings(&mut env, &fields) else { return std::ptr::null_mut(); };
    int_array(&env, &crate::timeline::schedule_order(&fields))
        .map(JIntArray::into_raw).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_dayRowOffsetsNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    counts: JIntArray<'_>,
) -> jintArray {
    let Ok(counts) = read_ints(&env, counts) else { return std::ptr::null_mut(); };
    let Some(offsets) = crate::timeline::day_row_offsets(&counts) else {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Too many timetable rows");
        return std::ptr::null_mut();
    };
    int_array(&env, &offsets).map(JIntArray::into_raw).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_weekLessonIndicesNative(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    lesson_weeks: JIntArray<'_>,
    selected: jint,
) -> jintArray {
    let Ok(weeks) = read_ints(&env, lesson_weeks) else { return std::ptr::null_mut(); };
    int_array(&env, &crate::timeline::week_lesson_indices(&weeks, selected))
        .map(JIntArray::into_raw).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_shouldAppendDaysNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    last_visible: jint,
    row_count: jint,
    last_day: JString<'_>,
) -> jboolean {
    let day = from_java(&mut env, &last_day);
    u8::from(crate::timeline::should_append_days(last_visible, row_count, &day))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_widgetLayoutNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    width: jint,
    min_height: jint,
) -> jint {
    crate::widget::layout(width, min_height) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_widgetDensityNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    count: jint,
    height: jint,
    layout: jint,
) -> jintArray {
    let Ok(layout) = crate::widget::Layout::try_from(layout) else {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Unknown widget layout");
        return std::ptr::null_mut();
    };
    int_array(&env, &crate::widget::density(count, height, layout))
        .map(JIntArray::into_raw).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_examSyncIssueNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    updated: jboolean,
    errors: JObjectArray<'_>,
) -> jstring {
    let Ok(errors) = read_strings(&mut env, &errors) else { return std::ptr::null_mut(); };
    match crate::portal::exam_sync_issue(semester, updated != 0, &errors) {
        Some(issue) => into_java(&mut env, issue),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_timelineBatchNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    lesson_dates: JObjectArray<'_>,
    lesson_slots: JIntArray<'_>,
    exam_fields: JObjectArray<'_>,
    exam_slots: JIntArray<'_>,
    period_fields: JObjectArray<'_>,
    period_numbers: JIntArray<'_>,
) -> jbyteArray {
    let result = (|| {
        let lesson_dates = read_strings(&mut env, &lesson_dates)?;
        let lesson_slots = read_ints(&env, lesson_slots)?;
        let exam_fields = read_strings(&mut env, &exam_fields)?;
        let exam_slots = read_ints(&env, exam_slots)?;
        let period_fields = read_strings(&mut env, &period_fields)?;
        let period_numbers = read_ints(&env, period_numbers)?;
        Ok::<_, jni::errors::Error>(crate::timeline::timeline_batch(
            &lesson_dates, &lesson_slots, &exam_fields, &exam_slots, &period_fields, &period_numbers,
        ))
    })();
    match result {
        Ok(batch) => into_byte_array(&mut env, batch),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_accountChangedNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    previous: JString<'_>,
    username: JString<'_>,
) -> jboolean {
    let previous = (!previous.is_null()).then(|| from_java(&mut env, &previous));
    let username = from_java(&mut env, &username);
    u8::from(crate::auth::account_changed(previous.as_deref(), &username))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_rotationFieldsNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    refresh: JString<'_>,
    idpc: JString<'_>,
) -> jint {
    let refresh = from_java(&mut env, &refresh);
    let idpc = from_java(&mut env, &idpc);
    crate::auth::rotation_fields(&refresh, &idpc)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_renewalActionNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    http_status: jint,
    io_failure: jboolean,
    access: JString<'_>,
) -> jint {
    let access = from_java(&mut env, &access);
    crate::auth::renewal_action(http_status, io_failure != 0, &access) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_renewalStartNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    current: JString<'_>,
    rejected: JString<'_>,
    refresh: JString<'_>,
) -> jint {
    let current = from_java(&mut env, &current);
    let rejected = from_java(&mut env, &rejected);
    let refresh = from_java(&mut env, &refresh);
    crate::auth::renewal_start(&current, &rejected, &refresh) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_requestKindNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    scheme: JString<'_>,
    host: JString<'_>,
    path: JString<'_>,
) -> jint {
    let scheme = from_java(&mut env, &scheme);
    let host = from_java(&mut env, &host);
    let path = from_java(&mut env, &path);
    crate::auth::request_kind(&scheme, &host, &path) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_shouldAutoRefreshNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    now: jlong,
    last_attempt: jlong,
) -> jboolean {
    u8::from(crate::autosync::should_auto_refresh(now, last_attempt))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_moveWeekNative(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    weeks: JIntArray<'_>,
    current: jint,
    delta: jint,
) -> jint {
    let Ok(weeks) = read_ints(&env, weeks) else { return -1; };
    crate::timeline::move_week_index(&weeks, current, delta).unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_canStartExamSyncNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    show_exams: jboolean,
    force: jboolean,
    fetching: jboolean,
) -> jboolean {
    u8::from(crate::timeline::can_start_exam_sync(show_exams != 0, force != 0, fetching != 0))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_syncFailureDetailNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    issue: JString<'_>,
    warning: JString<'_>,
    manual: jboolean,
) -> jstring {
    let issue = from_java(&mut env, &issue);
    let warning = from_java(&mut env, &warning);
    into_java(&mut env, crate::timeline::sync_failure_detail(&issue, &warning, manual != 0))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_shouldRecoverEmptyTimetableNative(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
    has_cache: jboolean,
    last_attempt: jlong,
    last_success: jlong,
    already_attempted: jboolean,
) -> jboolean {
    u8::from(crate::autosync::should_recover_empty_timetable(
        has_cache != 0, last_attempt, last_success, already_attempted != 0,
    ))
}
