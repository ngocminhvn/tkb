# Rust migration — TKB SV

## Portal commits, session policy and timeline batches

The native DAO validates and commits timetable response bytes in one JNI call.
The exam commit path preserves prior results when a portal batch is empty.
Rust selects the refresh outcome and which token fields to update. The Android
transport, refresh lock and encrypted preferences retain their roles.
`TKB1Y` batches timeline dates, display times and source indices for Compose.

## Current stage C — full native DAO and production handoff

`AppDatabase.get` now owns a singleton `RustCacheStore`, opened lazily on IO.
All DAO methods, including timetable/exam replacements and legacy fixture
cleanup, call native SQLite. The Android app keeps the existing database filename,
schema, package ID, and release signing configuration. Room, KSP, and
DataStore have been removed from the Android app dependencies.

New bounded batch codec reuses TKB1T/TKB1E; TKB1C carries counts. Existing
TKB1N carries notes. Invalid headers, counts, UTF-8, truncation and trailing
bytes fail before any delete/insert. Transactions execute in one native call.
Flow publishes after commit; notes have independent invalidation. Repository
cache Flow reads weeks and lessons together. ViewModel preloads asynchronously
and retains cancellation and first-login cache guard behavior.

Validation: 48 Rust tests and strict Clippy pass. The actual Kotlin DAO/encoder/
decoder compiles and runs against the host JNI library: binary round-trips,
read/write, failed-scope preservation, exam isolation, note Flow, persistent
reopen, deletes and closed-handle errors pass. CI compiles the Kotlin database facade and DAO contracts with an Android
Context stub for the host JNI smoke test before signed APK packaging.

On Android, isolated debug-signed APKs verified a Room v4 upgrade from
`d6ecd60` to `e89f923`, rollback to the Room build, then a direct upgrade
from Room to the Room-free build with R8 enabled. Existing periods, weeks,
lessons, exam IDs and notes survived. A fresh install of the Room-free build
also passed. Release-signed production upgrade and live TNUT login were not
tested. Older stage notes below describe historical intermediate work.

## Continuation from d6ecd60 (2026-11-16)

Work directly on `main`, per the latest user instruction. The temporary branch
`backup-kotlin-to-rust-d6ecd60` was deleted locally and remotely; no new branch
is retained. The older migration history below is retained for context.
Do not repeat the features implemented by `2dfa4d2` or `d6ecd60`.

### Stage A — SQLite backend preparation (not Android activation)

- [x] Add `rusqlite` with bundled SQLite and a locked dependency graph.
- [x] Implement typed rows and all DAO read/insert/delete operations in
  `rust-core/src/database/{mod,records}.rs`. Reactive Flow observation still
  belongs to the future Kotlin adapter; these are synchronous worker APIs.
- [x] Implement atomic timetable, semester exams and exam-type replacements;
  keep notes independent and reject records with mismatched semester/type.
- [x] Read weeks and lessons from one SQLite transaction.
- [x] Implement schema v1→v2→v3→v4 upgrades at the existing file path, without
  user-data deletion, schema version changes or copying a live database file.
- [x] Reject future, unversioned-with-user-tables and incompatible schemas.
- [x] Preserve an existing v4 Room identity; after older-schema upgrades,
  remove only `room_master_table` metadata so Room can revalidate the schema
  and regenerate its hash if a Room-based v4 APK is installed later.
- [x] Test rollback after partial insertion failures, independent exam types,
  exact legacy-fixture cleanup, notes, UTF-8, bound parameters, auto IDs,
  each supported schema version, committed WAL reads and coherent snapshots.
- [x] Cross-check native columns and SQLite types against the actual Kotlin
  Room entities in `AppDatabase.kt`.
- [x] Add host test and strict Clippy checks before the CI release build.
- [ ] Connect a JNI database adapter with binary batch transfers, explicit
  errors, serialized connection ownership and off-main-thread operations.
- [ ] Replace Room Flow invalidation with commit-driven StateFlow emissions;
  cover repository, widget and reminder writers and readers.
- [ ] Switch `AppDatabase.get` after installed-APK upgrade tests pass.
- [ ] Remove Room/KSP dependencies and old Kotlin implementation only after
  the replacement is verified. **APK still uses Room today.**

Do not open the same file simultaneously through Android Room/platform SQLite
and the bundled native SQLite library. Close the old backend before opening the
new one; they have separate invalidation/connection lifecycles. The host WAL test
uses two connections to the same bundled SQLite library, and does not prove
mixed-library access is safe. JNI must get the existing `tkb.db` path from
`Context.getDatabasePath`; retain WAL/SHM files and SQLite recovery behavior.

### Required validation before activation

Host tests verify backend semantics; they do not verify Room's actual generated
schema/identity validation or installed Android behavior. Test upgrade from real
v1/v2/v3/v4 APK databases and rollback to the current Room-based v4 APK, retaining
notes, cache and exam IDs. Test a fresh installation, worker-thread reads,
concurrent sync, widget/reminder reads, and Flow emission only after commit.

Login/Keystore, HTTP transport, refresh locking and credentials are untouched.
`com.tkb.sv`, JNI contracts already used by Android, signing configuration and
Compose are unchanged. Network/session/state migration, remaining ViewModel
work, Settings/alarm work and on-device performance measurements remain pending.
Do not infer completion of those phases from the new database backend.

### Validation results for this stage

- `cargo test --locked --manifest-path rust-core/Cargo.toml`: 44 tests passed
  (9 database tests and 35 existing tests).
- `cargo clippy --locked --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings`:
  passed with no warnings.
- `bash scripts/build-rust-android.sh`: passed with NDK r27d and the locked
  dependency graph for arm64-v8a, armeabi-v7a, x86_64 and x86. ELF architectures
  match; all 58 existing JNI entry points are present in every library. No
  dynamic dependency on Android's platform libsqlite is introduced.
- `assembleDebug`: attempted. Using a writable Gradle cache and the environment
  proxy resolved the initial wrapper-download failure. Gradle then failed
  because `/usr/lib/jvm/java-21-openjdk-amd64` lacks `JAVA_COMPILER` capability.
  Android SDK and emulator are also not installed in this environment; no APK
  build success is claimed. CI still sets up JDK 17 and Android tooling.
- Fresh login, signed upgrade/install-over, refresh/offline sync, notification,
  widget, APK update, first-frame/scroll/startup performance: not run; require an
  Android device/emulator, built APK and an authorized TNUT test session.

## Phase 1: HTTP/authentication — started
- [x] Move official TNUT origin checking, rotated access-token reuse policy,
  Bearer header parsing, 401 retry eligibility, and HTTP 5xx outage fallback
  policy to Rust, with unit tests.
- [x] Consolidate logical HTTP 200 / code 401 detection in Rust.
- [x] Parse login and refresh tokens and avatar responses in Rust. Build timetable and exam requests and handle exam pagination and deduplication in Rust.
- [ ] Replace Retrofit/OkHttp transport and session renewal ONLY after verified
  login, HTTP 401 and logical 401, refresh token rotation, timeout/retry,
  redirect-host, TLS and Android Keystore compatibility tests on device.
- Preserve existing encrypted preferences and synchronized refresh lock.

## Phase 2: Database — Rust SQLite active
- Rust SQLite preserves tkb.db, schema v4, migrations v1 to v4, notes, exams
  and cached lessons. The UI observes the native DAO through Kotlin Flow.
- Failed portal requests must never delete the previous offline cache.

## Phase 3: State/logic — started
- [x] Move 06:30 Vietnam once-daily auto-sync decision into Rust, preserving
  the existing no-polling behavior and Kotlin function signatures.
- [x] Move week/day selection, week bounds lookup, elastic swipe physics,
  edge navigation confirmation, cache priority arbitration, manual week resolution,
  extended date window calculation, and date window generation into Rust (`timeline.rs`).
- [x] Move local-date formatting, lesson time labels, widget end-of-day decisions, and reminder text ordering to Rust.
- [x] Move lesson selection, timeline ordering and row offsets, prefetch thresholds, exam sync outcomes, and widget sizing to Rust. Cache the week payload in the JNI adapter.
- [ ] Evaluate ViewModel sync state with Android lifecycle tests. The first-frame cache snapshot uses a single SQLite transaction.

## Phase 4: UI — retained in Kotlin/Compose
- Keep the existing Compose interface unchanged, per the current request.
- Avoid moving frequent, small UI calculations across JNI unless measurements
  demonstrate lower latency. No Rust UI prototype is in scope.

## Phase 5: Android bridge — pending
- [x] Move release-manifest parsing, SHA-256 verification, and XZ decompression to Rust.
- Preserve receivers, reminders, widget, Keystore and APK install integration.
  NativeActivity alone is not a replacement for BroadcastReceiver/AppWidgetProvider.

## Verification before each destructive switch
Run cargo test and cargo clippy --all-targets -D warnings for the Rust crate;
build all four ABIs and APK with Gradle, then test upgrade, login, refresh
failure, offline cold start, first sync, notifications, widget and app updates
on a real Android device. Compilation does not prove runtime parity.

## Stage B — staged cache/notes JNI adapter

Baseline: 5eb9d21. Backup for this continuation: `backup-rusqlite-5eb9d21`,
retained on GitHub per the latest request. Work commits remain on main.

- `database/bridge.rs`: synchronized checked handle registry; queries and close
  share ownership lock, closed handles cannot access freed memory.
- `database/jni.rs`: explicit Java exceptions on database failure; no silent empty
  cache on failure and no SQLite details/user data in exception text.
- `database/wire.rs`: cache reuses existing TKB1T layout with zero period rows;
  notes use TKB1N + count + UTF-8 id/note + big-endian i64 timestamp.
- `RustCacheStore.kt`: IO dispatch, coroutine Mutex, cancellation-safe opening
  and closing, commit-driven note Flow with distinctUntilChanged, no polling.
  All writes/observers must share one instance. Existing Room must be closed
  before opening this adapter against the existing Context database path.
- Existing PortalBinaryDecoder decodes TKB1N using its checked reader. Existing
  TKB1 formats and exported methods are unchanged. R8 preserves new JNI names.

This is not the production database switch: remaining timetable/exam DAO bridge
methods, corresponding invalidation, actual Room-to-native handoff and Android
upgrade/instrumentation validation are still pending. Login, 06:30 auto-sync,
Compose, signing and production database selection are unchanged.

Validation: cargo test (46 passed), strict all-target Clippy passed. Native
four-ABI build result is recorded in the stage report. Android Flow/JNI runtime,
installed upgrade, account login/refresh, widget/reminder/update and performance
tests have not run; no device/emulator or authorized TNUT session is available.
