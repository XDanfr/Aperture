#!/usr/bin/env bash
# Builds the Windows installers (MSI and EXE) with libmpv bundled, and checks the app finds it.
#
#   tools/desktop/package_windows.sh           (Git Bash on Windows, as in CI)
#
# Output: desktop/build/dist/Aperture-<version>-windows-x64.msi and .exe. jpackage downloads WiX
# itself through the Compose plugin; no Visual Studio needed. The installers aren't code-signed,
# so SmartScreen warns on first run (docs/DESKTOP.md).
set -euo pipefail

cd "$(dirname "$0")/../.."
version="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' desktop/build.gradle.kts)"
dist="desktop/build/dist"
mkdir -p "$dist"

tools/desktop/fetch_libmpv_windows.sh
./gradlew --no-configuration-cache :desktop:createDistributable :desktop:packageMsi :desktop:packageExe

app="desktop/build/compose/binaries/main/app/Aperture"
test -f "$app/app/resources/libmpv-2.dll" || { echo "libmpv-2.dll is missing from the app" >&2; exit 1; }

# The launcher is a GUI program with no console, so the result comes back through a file.
check="$(mktemp)"
"$app/Aperture.exe" --check-libmpv "$check" || { cat "$check" >&2; exit 1; }
cat "$check"
grep -qi "resources[/\\\\]libmpv-2.dll" "$check" || { echo "The app didn't load the bundled libmpv" >&2; exit 1; }

cp desktop/build/compose/binaries/main/msi/*.msi "$dist/Aperture-$version-windows-x64.msi"
cp desktop/build/compose/binaries/main/exe/*.exe "$dist/Aperture-$version-windows-x64.exe"
ls -l "$dist"
