# TKB SV — Rust business core (Android only)

The Rust core provides the platform-independent logic behind the existing Android app and `com.tkb.sv` application ID.

## Rust code used by the Android app

- Date normalization, week and month navigation, local dates using the device's UTC offset, Vietnam auto-sync timing, timetable selection and gestures.
- Lesson time labels, exam end times, the widget's end-of-day decision and sorted reminder text.
- TNUT login and refresh response parsing, avatar profile extraction, timetable and exam request bodies, logical unauthorized response handling and retry policy.
- Timetable and exam validation, pagination and cross-page deduplication before Room transactions.
- GitHub Pages release manifest validation, SHA-256 verification and streaming XZ decompression of downloaded APK packages in app-private storage.

`RustPortalCore` passes original HTTP response bytes to Rust through JNI. It decodes the returned `TKB1` records into Room rows, session values, avatar details and release metadata. The timetable and exam records retain the existing layouts. The updater supports compressed and uncompressed packages. Rust verifies the downloaded bytes before writing the final APK.

## Android integration

Kotlin owns Compose, Room transactions and Flow, Keystore-backed preferences, HTTP/TLS via Retrofit and OkHttp, the synchronized session refresh, Android alarms and notifications, RemoteViews, and the system APK installer. Network calls and native portal conversion run off the UI thread. Each timetable and exam source keeps its cached data when a request or validation fails.

## Build

Install the Android NDK, Rust and cargo-ndk:

```bash
cargo install cargo-ndk --locked
cargo test --locked --manifest-path rust-core/Cargo.toml
cargo clippy --locked --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings
bash scripts/build-rust-android.sh
./gradlew assembleDebug
```

The native `libtkb_core.so` is built for arm64-v8a, armeabi-v7a,
x86_64, and x86. Generated binaries are ignored by Git. Gradle fails
if any expected library is missing; GitHub Actions builds and verifies
the native libraries inside the APK.
