//! JNI entry points for the TKB SV Android app. Data never leaves the process.
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jbyteArray, jint, jstring};
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
    source: JByteArray<'_>,
) -> jbyteArray {
    let bytes = match env.convert_byte_array(&source) {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };
    into_byte_array(&mut env, crate::portal::map_timetable_wire(semester, &bytes))
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
