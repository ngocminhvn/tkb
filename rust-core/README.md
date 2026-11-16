# TKB SV — Rust business core (Android only)

The Rust core provides the platform-independent logic behind the existing Android app and `com.tkb.sv` application ID.

## Rust code used by the Android app

- Date normalization, week and month navigation, local dates using the device's UTC offset, Vietnam auto-sync timing, timetable selection and gestures.
- Weekly lesson selection, batched timeline dates, time labels and stable ordering, day-row offsets and timeline prefetch. The Kotlin adapter reuses the packed week payload.
- Lesson time labels, exam end times, widget sizing and end-of-day decisions, and sorted reminder text.
- TNUT login and refresh response parsing, avatar profile extraction, timetable and exam request bodies, logical unauthorized response handling, refresh decisions, token rotation and account-change policy.
- Timetable and exam validation, pagination and cross-page deduplication and portal-specific commits that preserve valid cached data when a source fails or returns an empty exam list.
- GitHub Pages release manifest validation, SHA-256 verification and streaming XZ decompression of downloaded APK packages in app-private storage.

`RustPortalCore` passes original HTTP response bytes to Rust through JNI. It decodes the returned `TKB1` records into Kotlin records, session values, avatar details and release metadata. The timetable and exam records retain the existing layouts. The updater supports compressed and uncompressed packages. Rust verifies the downloaded bytes before writing the final APK.

## Android integration

`src/database/` owns SQLite queries, CRUD and transactions for `tkb.db` v4.
`RustCacheStore` implements the complete Kotlin DAO with bounded binary batches.
The process-owned facade serves UI, widgets and reminders. Cache Flow reads
weeks/lessons atomically; note and timetable invalidation are separate.
Opening is lazy on IO, and the ViewModel preloads asynchronously.
Rust SQLite reuses the database file and schema created by earlier Room versions.

Kotlin still owns Compose, Flow adaptation, Keystore, Retrofit/OkHttp transport,
session refresh locking, alarms, notifications, RemoteViews and APK installation.
Host JNI smoke tests compile the actual Kotlin adapter, use real native SQLite,
and verify persistence and Flow. See the migration checklist for the Android
upgrade verification and remaining production checks.
[Migration checklist](../docs/RUST_MIGRATION.md)

## Build

Install the Android NDK, Rust and cargo-ndk:

```bash
cargo install cargo-ndk --locked
cargo test --locked --manifest-path rust-core/Cargo.toml
cargo clippy --locked --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings
bash scripts/build-rust-android.sh
./gradlew assembleRelease
```

The native `libtkb_core.so` is built for arm64-v8a, armeabi-v7a,
x86_64, and x86. Generated binaries are ignored by Git. Gradle fails
if any expected library is missing; GitHub Actions builds and verifies
the native libraries inside the APK.
