#!/usr/bin/env bash
# Builds Aperture.app with libmpv inside it, ad-hoc signs it and wraps it in a DMG.
#
#   brew install mpv
#   tools/desktop/package_macos.sh [--sources]
#
# --sources also collects the source archive of every bundled library (for GPL compliance; see
# docs/DESKTOP.md) into desktop/build/dist/macos-<arch>-sources/.
#
# Output: desktop/build/dist/Aperture-<version>-macos-<arch>.dmg. Works on Intel and Apple
# Silicon, locally and in CI. No Apple Developer account needed: the app is ad-hoc signed, not
# notarised, so first launch needs right-click > Open (docs/DESKTOP.md explains).
set -euo pipefail

cd "$(dirname "$0")/../.."
root="$(pwd)"
case "$(uname -m)" in
  arm64) arch=arm64 ;;
  x86_64) arch=x64 ;;
  *) echo "Unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac
version="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' desktop/build.gradle.kts)"
dist="$root/desktop/build/dist"
mkdir -p "$dist"

echo "==> Bundling libmpv ($arch)"
bundle_args=()
if [ "${1:-}" = "--sources" ]; then
  bundle_args+=(--sources "$dist/macos-$arch-sources")
fi
python3 tools/desktop/bundle_libmpv_macos.py ${bundle_args[@]+"${bundle_args[@]}"}

echo "==> Building the app"
./gradlew --no-configuration-cache :desktop:createDistributable

# Sign a copy, so Gradle's own output stays as it built it.
stage="$dist/macos-$arch"
rm -rf "$stage"
mkdir -p "$stage"
ditto desktop/build/compose/binaries/main/app/Aperture.app "$stage/Aperture.app"
app="$stage/Aperture.app"

test -f "$app/Contents/app/resources/libmpv.2.dylib" || {
  echo "libmpv.2.dylib is missing from the app's resources" >&2
  exit 1
}

echo "==> Ad-hoc signing"
# Inside out: the bundled libraries, then the whole app. Ad-hoc ("-") needs no certificate.
find "$app/Contents/app/resources" -type f \( -name '*.dylib' -o -name '*.jnilib' \) -print0 |
  xargs -0 codesign --force --sign - --timestamp=none
codesign --force --deep --sign - --timestamp=none "$app"
codesign --verify --deep --strict --verbose=1 "$app"

echo "==> Checking the app loads its own libmpv"
check_out="$(mktemp)"
dyld_log="$(mktemp)"
DYLD_PRINT_LIBRARIES=1 "$app/Contents/MacOS/Aperture" --check-libmpv "$check_out" 2>"$dyld_log" || {
  cat "$check_out" "$dyld_log" >&2
  exit 1
}
cat "$check_out"
grep -q "$app/Contents/app/resources/libmpv.2.dylib" "$check_out" || {
  echo "The app didn't load the bundled libmpv" >&2
  exit 1
}
# Every library dyld loaded must come from the app or the OS (JNA unpacks its own stub to a cache).
outside="$(sed -n 's/^dyld\[[0-9]*\]: <[^>]*> //p' "$dyld_log" |
  grep -v -e "^$app/" -e '^/usr/lib/' -e '^/System/' -e '/JNA/' -e '/jna' || true)"
if [ -n "$outside" ]; then
  echo "Loaded from outside the app:" >&2
  echo "$outside" >&2
  exit 1
fi
rm -f "$check_out" "$dyld_log"

echo "==> Making the DMG"
dmg="$dist/Aperture-$version-macos-$arch.dmg"
contents="$(mktemp -d)"
cp -R "$app" "$contents/"
ln -s /Applications "$contents/Applications"
rm -f "$dmg"
hdiutil create -quiet -volname "Aperture" -srcfolder "$contents" -fs HFS+ -format UDZO -ov "$dmg"
rm -rf "$contents"

# The copy inside the DMG must still verify.
mount_point="$(mktemp -d)"
hdiutil attach -quiet -nobrowse -readonly -mountpoint "$mount_point" "$dmg"
codesign --verify --deep --strict "$mount_point/Aperture.app"
hdiutil detach -quiet "$mount_point"

echo "==> $dmg ($(du -h "$dmg" | cut -f1))"
