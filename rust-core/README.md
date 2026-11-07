# TKB SV Rust Core — Android only

This native Rust crate is called from the actual Android APK via the JNI
`RustScheduleCore` bridge. There is **no iOS target**.

## Implemented and used by the Android app

- TNUT date normalization across portal input formats.
- End-time calculation for exams.
- One-day calendar navigation, including week/month/year boundaries.
- Seven-day calendar generation.

The existing UI remains Jetpack Compose. Android Room/SQLite, network
authentication/token refresh, reminder scheduling and widgets still run
through Android/Kotlin. They are not yet Rust implementations. This is
**not** a fully migrated application.

## Build

Requires Rust, rustup, cargo-ndk and an Android NDK (API 24+).

```bash
cargo install cargo-ndk --locked
cargo test --manifest-path rust-core/Cargo.toml
bash scripts/build-rust-android.sh
gradle testDebugUnitTest assembleDebug
```

The build script compiles native `libtkb_core.so` for `arm64-v8a`,
`armeabi-v7a`, `x86_64` and `x86` into the ignored
`app/src/main/jniLibs/` tree. Gradle refuses to build an APK
when any of the required libraries are missing. GitHub Actions compiles
and verifies these libraries automatically.

Plain JVM tests use equivalent Kotlin routines because they do not load
Android shared libraries; Rust has separate Cargo unit tests. Real
Android devices run the Rust implementations.

## Remaining migration work

1. Replace Retrofit/OkHttp TNUT API and HTTP 200 logical-401 handling
   without regressing session renewal or redirect protections.
2. Move offline synchronization/model mapping to Rust, retaining existing
   portal-data provenance and test fixtures.
3. Design a safe Room-to-Rust SQLite migration that preserves existing
   schema, saved periods/weeks, classes, exams, and lesson notes.
4. Keep Android framework integration (Compose, notifications, widgets,
   keystore and app update installer) as Android-specific bindings.

No credential storage, database schema, or saved notes are changed by this
initial JNI migration.
