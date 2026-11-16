# Rust migration — TKB SV

Backup snapshot: backup branch at commit 682843618559870809f9bed7fe0b03f0153f9327.
Migrate main in small, testable steps. Never delete the backup, change the
application ID, change release signing credentials or use destructive DB migrations.

## Phase 1: HTTP/authentication — started
- [x] Move official TNUT origin checking and rotated access-token reuse policy
  to Rust, with unit tests.
- [x] Consolidate logical HTTP 200 / code 401 detection in Rust.
- [ ] Replace Retrofit/OkHttp transport and session renewal ONLY after verified
  login, HTTP 401 and logical 401, refresh token rotation, timeout/retry,
  redirect-host, TLS and Android Keystore compatibility tests on device.
- Preserve existing encrypted preferences and synchronized refresh lock.

## Phase 2: Database — pending
- Replace Room with a Rust SQLite adapter while preserving tkb.db, schema v4,
  migrations v1 to v4, notes, exams and all cached lessons.
- Port Room transaction semantics before switching the UI to Rust state.
- Failed portal requests must never delete the previous offline cache.

## Phase 3: State/logic — started
- [x] Move 06:30 Vietnam once-daily auto-sync decision into Rust, preserving
  the existing no-polling behavior and Kotlin function signatures.
- [x] Move week/day selection, week bounds lookup, elastic swipe physics,
  edge navigation confirmation, and cache priority arbitration into Rust (`timeline.rs`).
- [ ] Move lazy loaded date windows, first-frame snapshots, sync statuses
  and observable state from ViewModel to Rust.

## Phase 4: UI — pending
- Prototype a Rust UI separately (Slint is a candidate) with gestures, smooth
  scrolling, IME, accessibility and dark mode. Do not delete Compose until
  device tests demonstrate feature parity and supported minSdk.

## Phase 5: Android bridge — pending
- Preserve receivers, reminders, widget, Keystore and APK install integration.
  NativeActivity alone is not a replacement for BroadcastReceiver/AppWidgetProvider.

## Verification before each destructive switch
Run cargo test and cargo clippy --all-targets -D warnings for the Rust crate;
build all four ABIs and APK with Gradle, then test upgrade, login, refresh
failure, offline cold start, first sync, notifications, widget and app updates
on a real Android device. Compilation does not prove runtime parity.
