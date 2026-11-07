//! JNI entry points for the TKB SV Android app. Data never leaves the process.
use jni::objects::{JClass, JString};
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
    _class: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::normalize_date(&source))
}

#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustScheduleCore_examEndTimeNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
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
    _class: JClass<'_>,
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
    _class: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    let source = from_java(&mut env, &input);
    into_java(&mut env, crate::calendar_week_dates(&source).join(","))
}
