#!/usr/bin/env bash
# Signs the locally built release APKs the same way CI's "Build signed APKs" workflow does.
#
#   ./gradlew :app:assembleStandardRelease :app:assembleCreatorRelease :mobile:assembleRelease
#   tools/sign_release_apks.sh [keystore] [alias]
#
# Defaults to ~/Aperture.jks and alias key0. Passwords are asked for once and never written down.
# Signed APKs land in signed-apks/ with a SHA256SUMS.txt, as in CI.
set -euo pipefail

cd "$(dirname "$0")/.."
keystore="${1:-$HOME/Aperture.jks}"
alias="${2:-key0}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
tools="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"

test -f "$keystore" || { echo "Keystore not found: $keystore" >&2; exit 1; }

read -r -s -p "Keystore password for $keystore: " KEYSTORE_PASSWORD; echo
read -r -s -p "Key password for $alias (Enter if the same): " KEY_PASSWORD; echo
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
export KEYSTORE_PASSWORD KEY_PASSWORD

umask 077
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p signed-apks

for entry in \
  "standard|app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk" \
  "creator|app/build/outputs/apk/creator/release/app-creator-release-unsigned.apk" \
  "companion|mobile/build/outputs/apk/release/mobile-release-unsigned.apk"; do
  variant="${entry%%|*}"
  source="${entry#*|}"
  if [ ! -f "$source" ]; then
    echo "Skipping $variant: $source not built" >&2
    continue
  fi
  aligned="$work/aperture-$variant-aligned.apk"
  output="signed-apks/Aperture-$variant-release.apk"
  "$tools/zipalign" -P 16 -f 4 "$source" "$aligned"
  "$tools/apksigner" sign --ks "$keystore" --ks-key-alias "$alias" \
    --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
    --out "$output" "$aligned"
  "$tools/apksigner" verify "$output"
  "$tools/zipalign" -c -P 16 4 "$output"
  echo "Signed $output"
done

(cd signed-apks && shasum -a 256 *.apk > SHA256SUMS.txt)
echo "Done: $(pwd)/signed-apks"
