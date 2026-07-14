#!/usr/bin/env bash
#
# Builds PJSIP static libs for iOS (device arm64 + simulator arm64/x86_64) and
# assembles pjsip.xcframework for the plugin to vendor.
#
# Prereqs: Xcode + command line tools; network access. Run on macOS.
# Usage:   ./build-ios.sh
#
# Output:  ../ios/pjsip.xcframework  (+ ../ios/pjsip-headers/ umbrella copy)
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PJ_VERSION="2.15.1"
MIN="14.0"
WORK="$HERE/.work-ios"
SRC="$WORK/pjproject-$PJ_VERSION"
OUT="$HERE/../ios/pjsip.xcframework"

DEV_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
SIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"

mkdir -p "$WORK"
if [ ! -d "$SRC" ]; then
  echo "==> Fetching pjproject $PJ_VERSION"
  curl -fsSL "https://github.com/pjsip/pjproject/archive/refs/tags/$PJ_VERSION.tar.gz" \
    | tar -xz -C "$WORK"
fi
cp "$HERE/config_site.h" "$SRC/pjlib/include/pj/config_site.h"

# Build one slice and return the path to a single merged static lib for it.
# $1=arch  $2=sdkpath  $3=min-version-flag
build_slice () {
  local ARCH_NAME="$1" SDKPATH="$2" MINFLAG="$3" TAG="$1"
  echo "==> Building iOS slice $TAG ($SDKPATH)" >&2
  ( cd "$SRC"
    # Stale/truncated .depend files break make at parse time — always start clean.
    find . -name '.*.depend' -delete
    make distclean >/dev/null 2>&1 || true
    # --disable-*: keep configure from autodetecting host (homebrew) libraries
    # that we don't cross-compile in P0 (OpenSSL/opus/vpx/sdl/ffmpeg/openh264).
    ARCH="-arch $ARCH_NAME" IPHONESDK="$SDKPATH" MIN_IOS="$MINFLAG" \
      ./configure-iphone \
        --disable-ssl --disable-opus --disable-vpx --disable-sdl \
        --disable-ffmpeg --disable-openh264 --disable-opencore-amr
    make dep && make clean && make
  ) >&2
  # Merge every produced module archive for this slice into one lib.
  local MERGED="$WORK/libpjsip-$TAG-$RANDOM.a"
  # shellcheck disable=SC2046
  libtool -static -o "$MERGED" $(find "$SRC" -name '*.a' -path '*/lib/*')
  echo "$MERGED"
}

DEV_A="$(build_slice arm64  "$DEV_SDK" "-miphoneos-version-min=$MIN")"
# Simulator slices use the simulator SDK + the simulator min-version flag.
SIM_ARM_A="$(build_slice arm64  "$SIM_SDK" "-mios-simulator-version-min=$MIN")"
SIM_X86_A="$(build_slice x86_64 "$SIM_SDK" "-mios-simulator-version-min=$MIN")"

SIM_A="$WORK/libpjsip-sim.a"
lipo -create "$SIM_ARM_A" "$SIM_X86_A" -output "$SIM_A"

# Collect PJSIP public headers (pjsua2 headers live under pjsip/include).
HEADERS="$WORK/headers"
rm -rf "$HEADERS"; mkdir -p "$HEADERS"
for m in pjlib pjlib-util pjnath pjmedia pjsip; do
  cp -R "$SRC/$m/include/." "$HEADERS/" 2>/dev/null || true
done
# Keep a copy next to the framework for the Swift/ObjC++ bridge to import.
rm -rf "$HERE/../ios/pjsip-headers"; cp -R "$HEADERS" "$HERE/../ios/pjsip-headers"

rm -rf "$OUT"
xcodebuild -create-xcframework \
  -library "$DEV_A" -headers "$HEADERS" \
  -library "$SIM_A" -headers "$HEADERS" \
  -output "$OUT"

echo "==> Done. $OUT  (headers also copied to ios/pjsip-headers/)"
