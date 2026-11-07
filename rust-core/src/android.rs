//! JNI entry points for the TKB SV Android app. Data never leaves the process.
use jni::objects::{JObject, JString};
use jni::sys::{jint, jstring};
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

fn return_or_throw(env: &mut JNIEnv<'_>, result: Result<String, String>) -> jstring {
    match result {
        Ok(value) => into_java(env, value),
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapTimetableNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    payload: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &payload);
    return_or_throw(&mut env, crate::portal::map_timetable(semester, &source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_mapExamPageNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    semester: jint,
    is_midterm: jni::sys::jboolean,
    payload: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &payload);
    return_or_throw(
        &mut env,
        crate::portal::map_exam_page(semester, is_midterm != 0, &source),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustPortalCore_dedupeExamsNative(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    payload: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &payload);
    return_or_throw(&mut env, crate::portal::dedupe_exam_rows(&source))
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
