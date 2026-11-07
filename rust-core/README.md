# TKB SV — Rust business core (Android only)

This is an **incremental** migration of the Android app, not a rewrite
of the UI. TKB SV keeps Kotlin + Jetpack Compose + Material 3 and the
existing `com.tkb.sv` application ID.

## Rust code now used by the Android app

- Calendar date normalization, next/previous day, seven-day week generation.
- Exam end time.
- Timetable payload validation, and conversion of TNUT period/week/lesson
  responses into the original Room entity columns.
- Each TNUT exam page: validation of required course/date fields, conversion
  into original exam columns, preserved postponed and midterm flags.
- Exam pagination decision and duplicate filtering across fetched pages.
- Student-ID normalization at login and lesson-note whitespace normalization.

The Rust engine is called via `RustPortalCore` and `RustScheduleCore`.
The JNI bridge now takes the **original HTTP response bytes** and returns
a compact versioned binary format (`TKB1`). Rust parses the TNUT JSON once,
then Kotlin reads binary Room-ready rows. The old Kotlin DTO -> JSON -> Rust ->
JSON -> Kotlin sequence is eliminated; cross-page duplicate filtering also
works directly on Room records without another JSON round-trip.

The network and native conversion run on `Dispatchers.IO` so reading the
response stream does not block Compose. Rust rejects malformed responses
before Room begins a replacement transaction. Errors preserve offline cache.

## Still Android/Kotlin (not fully migrated)

- Retrofit/OkHttp network calls and session renewal, including HTTP 200
  responses containing logical code 401 and the fallback login flow.
- Room database connection, transactional replacement and reactive Flow.
  SQLite remains `tkb.db`, schema version 4; no migration runs during
  this change, and existing lessons/notes/exams are retained.
- Android Keystore-backed preferences, notifications, widget, APK updating.
- Jetpack Compose UI and UI-bound ViewModels.

**Do not remove Kotlin network/DB adapters without tests for sessions,
transaction atomicity and Room migration compatibility.**

## Build and test

Install the Android NDK, Rust and cargo-ndk:

```bash
cargo install cargo-ndk --locked
cargo test --manifest-path rust-core/Cargo.toml
cargo clippy --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings
bash scripts/build-rust-android.sh
gradle testDebugUnitTest assembleDebug
```

The native `libtkb_core.so` is built for arm64-v8a, armeabi-v7a,
x86_64, and x86. Generated binaries are ignored by Git. Gradle fails
if any expected library is missing; GitHub Actions attempts to build and
verify the native libraries inside the APK.

Tests in `portal.rs` check empty/corrupt payload behavior and field
mapping. The Android host JVM tests check the JSON contract against
existing Room entity classes. On-device JNI/SQLite and network behavior
still need verification before the full migration can be considered done.
