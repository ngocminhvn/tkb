#!/usr/bin/env bash
# Build all supported Android ABI shared libraries from the shared Rust core.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if ! command -v cargo >/dev/null 2>&1 || ! command -v rustup >/dev/null 2>&1; then
  echo "Rust cargo and rustup are required: https://rustup.rs" >&2
  exit 1
fi
if ! cargo ndk --help >/dev/null 2>&1; then
  echo "cargo-ndk is required: cargo install cargo-ndk --locked" >&2
  exit 1
fi
if [[ -n "${ANDROID_NDK_HOME:-}" && ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME must point to a valid Android NDK" >&2
  exit 1
fi

rustup target add \
  aarch64-linux-android \
  armv7-linux-androideabi \
  x86_64-linux-android \
  i686-linux-android

mkdir -p "$REPO_ROOT/app/src/main/jniLibs"
cd "$REPO_ROOT/rust-core"
cargo ndk -p 24 \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86_64 \
  -t x86 \
  -o "$REPO_ROOT/app/src/main/jniLibs" \
  build --release

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  test -s "$REPO_ROOT/app/src/main/jniLibs/$abi/libtkb_core.so" || {
    echo "Missing Rust library for $abi" >&2
    exit 1
  }
done
echo "OK: Rust libraries compiled for all four Android ABIs"
