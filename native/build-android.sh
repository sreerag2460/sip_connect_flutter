#!/usr/bin/env bash
#
# Builds PJSIP (pjsua2 + JNI) for Android into the plugin's jniLibs.
#
# Prereqs: Android NDK r26 + a JDK (javac on PATH); macOS/Linux; network access.
# Usage:   ANDROID_NDK_ROOT=/path/to/ndk/26.3.11579264 ./build-android.sh
#
# Output:
#   ../android/src/main/jniLibs/<abi>/libpjsua2.so
#   ../android/src/main/jniLibs/<abi>/libc++_shared.so   (runtime dependency)
#   ../android/src/main/java/org/pjsip/pjsua2/*.java      (swig binding)
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PJ_VERSION="2.15.1"
SSL_VERSION="3.3.2"
API="21"
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")
WORK="$HERE/.work"
SRC="$WORK/pjproject-$PJ_VERSION"
SSL_SRC="$WORK/openssl-$SSL_VERSION"
JNILIBS="$HERE/../android/src/main/jniLibs"
JAVA_OUT="$HERE/../android/src/main/java"

: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to your NDK r26 path}"
command -v javac >/dev/null || { echo "error: javac (JDK) not on PATH"; exit 1; }

TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64"

mkdir -p "$WORK"

# 1) Fetch pjproject (pinned tag) once. symbols.i is shipped, no codegen needed.
if [ ! -d "$SRC" ]; then
  echo "==> Fetching pjproject $PJ_VERSION"
  curl -fsSL "https://github.com/pjsip/pjproject/archive/refs/tags/$PJ_VERSION.tar.gz" \
    | tar -xz -C "$WORK"
fi

if [ ! -d "$SSL_SRC" ]; then
  echo "==> Fetching OpenSSL $SSL_VERSION"
  curl -fsSL "https://github.com/openssl/openssl/releases/download/openssl-$SSL_VERSION/openssl-$SSL_VERSION.tar.gz" \
    | tar -xz -C "$WORK"
fi

# Static OpenSSL per ABI (for PJSIP TLS transport + DTLS-SRTP).
build_openssl () {
  local ABI="$1" TARGET EXTRA="" PREFIX="$WORK/ssl-$1"
  [ -f "$PREFIX/lib/libssl.a" ] && { echo "==> OpenSSL for $ABI already built"; return; }
  case "$ABI" in
    # 32-bit ARM asm has non-PIC relocations (OPENSSL_armcap_P) that can't be
    # linked into our shared libpjsua2.so — build that ABI without asm.
    armeabi-v7a) TARGET="android-arm"; EXTRA="no-asm";;
    arm64-v8a)   TARGET="android-arm64";;
    x86)         TARGET="android-x86"; EXTRA="no-asm";;
    x86_64)      TARGET="android-x86_64";;
  esac
  echo "==> Building OpenSSL $SSL_VERSION for $ABI"
  ( cd "$SSL_SRC"
    make distclean >/dev/null 2>&1 || true
    # shellcheck disable=SC2086
    PATH="$TOOLCHAIN/bin:$PATH" ./Configure "$TARGET" $EXTRA -fPIC -D__ANDROID_API__=$API \
      no-shared no-tests no-apps no-docs --prefix="$PREFIX" --libdir=lib >/dev/null
    PATH="$TOOLCHAIN/bin:$PATH" make -j8 build_libs >/dev/null
    PATH="$TOOLCHAIN/bin:$PATH" make install_dev >/dev/null
  )
}

# 2) Drop in our build config.
cp "$HERE/config_site.h" "$SRC/pjlib/include/pj/config_site.h"

for ABI in "${ABIS[@]}"; do
  if [ -f "$JNILIBS/$ABI/libpjsua2.so" ]; then
    echo "==> $ABI already built, skipping (delete $JNILIBS/$ABI to force)"
    continue
  fi
  build_openssl "$ABI"

  echo "==> Building PJSIP for $ABI"
  ( cd "$SRC"
    # Stale/truncated .depend files break make at parse time — always start clean.
    find . -name '.*.depend' -delete
    make distclean >/dev/null 2>&1 || true
    # --disable-*: keep configure from autodetecting host (homebrew) libraries
    # that we don't cross-compile (opus/vpx/sdl/ffmpeg/openh264). SSL comes
    # from our per-ABI static OpenSSL build.
    APP_PLATFORM="android-$API" TARGET_ABI="$ABI" \
      ./configure-android --use-ndk-cflags \
        --with-ssl="$WORK/ssl-$ABI" \
        --disable-opus --disable-vpx --disable-sdl \
        --disable-ffmpeg --disable-openh264 --disable-opencore-amr
    make dep && make clean && make

    # Build ONLY the Java pjsua2 binding (avoids the csharp/mono target).
    # The java Makefile's default target builds libpjsua2.so + copies
    # libc++_shared.so next to it, and generates the org.pjsip.pjsua2 sources.
    # Its output/ dir is NOT covered by distclean — clear it so the wrapper
    # object from the previous ABI can't leak into this ABI's link.
    ( cd pjsip-apps/src/swig/java && rm -rf output android/pjsua2/src/main/jniLibs && make )
  )

  # 3) Collect the .so + its libc++_shared.so dependency for this ABI.
  mkdir -p "$JNILIBS/$ABI"
  find "$SRC/pjsip-apps/src/swig/java" -name "libpjsua2.so"   -exec cp -f {} "$JNILIBS/$ABI/" \;
  find "$SRC/pjsip-apps/src/swig/java" -name "libc++_shared.so" -exec cp -f {} "$JNILIBS/$ABI/" \;
  [ -f "$JNILIBS/$ABI/libpjsua2.so" ] || { echo "error: libpjsua2.so not produced for $ABI"; exit 1; }
done

# 4) Copy the generated Java binding once (identical across ABIs).
echo "==> Copying org.pjsip.pjsua2 Java binding"
mkdir -p "$JAVA_OUT/org/pjsip/pjsua2"
cp "$SRC"/pjsip-apps/src/swig/java/android/pjsua2/src/main/java/org/pjsip/pjsua2/*.java \
   "$JAVA_OUT/org/pjsip/pjsua2/"

echo "==> Done."
echo "    libs    -> $JNILIBS/<abi>/{libpjsua2.so,libc++_shared.so}"
echo "    binding -> $JAVA_OUT/org/pjsip/pjsua2/"
