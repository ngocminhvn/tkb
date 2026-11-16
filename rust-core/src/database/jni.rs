//! JNI errors become exceptions, never empty-success cache responses.
use super::bridge;
use jni::{
    objects::{JObject, JString},
    sys::{jbyteArray, jint, jlong},
    JNIEnv,
};
fn failure(env: &mut JNIEnv<'_>) {
    let _ = env.throw_new(
        "java/lang/IllegalStateException",
        "Native database operation failed",
    );
}
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustCacheStore_openNative(
    mut env: JNIEnv<'_>,
    _: JObject<'_>,
    path: JString<'_>,
) -> jlong {
    let result = env
        .get_string(&path)
        .ok()
        .and_then(|path| bridge::open(&String::from(path)).ok());
    match result {
        Some(id) => id,
        None => {
            failure(&mut env);
            0
        }
    }
}
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustCacheStore_closeNative(
    mut env: JNIEnv<'_>,
    _: JObject<'_>,
    handle: jlong,
) {
    if bridge::close(handle).is_err() {
        failure(&mut env);
    }
}
fn bytes(env: &mut JNIEnv<'_>, result: rusqlite::Result<Vec<u8>>) -> jbyteArray {
    match result
        .ok()
        .and_then(|data| env.byte_array_from_slice(&data).ok())
    {
        Some(data) => data.into_raw(),
        None => {
            failure(env);
            std::ptr::null_mut()
        }
    }
}
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustCacheStore_cacheNative(
    mut env: JNIEnv<'_>,
    _: JObject<'_>,
    handle: jlong,
    semester: jint,
) -> jbyteArray {
    bytes(&mut env, bridge::cache(handle, semester))
}
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustCacheStore_notesNative(
    mut env: JNIEnv<'_>,
    _: JObject<'_>,
    handle: jlong,
) -> jbyteArray {
    bytes(&mut env, bridge::notes(handle))
}
#[no_mangle]
pub extern "system" fn Java_com_sv_tnu_rust_RustCacheStore_editNoteNative(
    mut env: JNIEnv<'_>,
    _: JObject<'_>,
    handle: jlong,
    operation: jint,
    id: JString<'_>,
    text: JString<'_>,
    timestamp: jlong,
) {
    let id = env.get_string(&id).map(String::from);
    let text = env.get_string(&text).map(String::from);
    let success = match (id, text) {
        (Ok(id), Ok(text)) => bridge::edit_note(handle, operation, &id, &text, timestamp).is_ok(),
        _ => false,
    };
    if !success {
        failure(&mut env);
    }
}
