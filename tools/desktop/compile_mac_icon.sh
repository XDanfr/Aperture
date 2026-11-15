#!/usr/bin/env bash
# Compiles the macOS icon, made with Icon Composer (desktop/icons/Aperture.icon), into what the
# app bundle needs:
#   desktop/icons/macos/Resources/Assets.car  the layered Liquid Glass icon (macOS 26 and later),
#                                             found through CFBundleIconName = Aperture
#   desktop/icons/Aperture.icns               flat renders of it for earlier macOS, 16 to 1024 px
# Both are committed, so building the app needs no Xcode. Rerun after editing the .icon.
# Needs Xcode 26 or later (actool).
set -euo pipefail

cd "$(dirname "$0")/../.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

xcrun actool "$PWD/desktop/icons/Aperture.icon" --compile "$work" \
  --app-icon Aperture --include-all-app-icons --standalone-icon-behavior all \
  --output-partial-info-plist "$work/partial.plist" \
  --enable-on-demand-resources NO --development-region en \
  --target-device mac --platform macosx --minimum-deployment-target 12.0 >/dev/null

mkdir -p desktop/icons/macos/Resources
cp "$work/Assets.car" desktop/icons/macos/Resources/Assets.car
cp "$work/Aperture.icns" desktop/icons/Aperture.icns
echo "Compiled desktop/icons/Aperture.icon"
