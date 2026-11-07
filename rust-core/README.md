# TKB SV Rust Core (Android only)

This is the first migration slice. It ports deterministic, platform-independent
schedule logic into a Rust crate with tests. The existing Android application
still executes its Kotlin implementations until an Android JNI/UniFFI bridge is
introduced and validated against the same fixtures. **This crate is not yet
linked into the APK.**

Run locally:

```bash
cargo test --manifest-path rust-core/Cargo.toml
cargo fmt --manifest-path rust-core/Cargo.toml --all -- --check
```

Migration order:
1. Verify Rust date/time functions against Kotlin behavior and portal fixtures.
2. Add an Android JNI/UniFFI bridge and cross-compile with the Android NDK.
3. Move portal parsing, API retry/token refresh, and schedule synchronization.
4. Migrate Room data access only with an explicit schema/data-preserving plan.
5. Keep Jetpack Compose, Android notifications, widgets, and platform keystore
   in Kotlin; never move credentials into unencrypted Rust storage.

Do not remove Kotlin logic until Android instrumentation tests and APK builds pass.
