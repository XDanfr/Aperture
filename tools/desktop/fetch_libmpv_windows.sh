#!/usr/bin/env bash
# Puts a prebuilt libmpv for Windows x64 into desktop/mpv-bundle/windows-x64/, which Compose
# copies into the installed app's resources folder, where LibMpv looks first.
#
#   tools/desktop/fetch_libmpv_windows.sh
#
# libmpv-2.dll comes from shinchiro/mpv-winbuild-cmake: one self-contained DLL with FFmpeg linked
# in. It imports vulkan-1.dll, which GPU drivers usually install but VMs, Windows Server and basic
# display drivers lack, and without it the DLL doesn't load at all; so the Khronos Vulkan loader
# from LunarG's runtime package goes next to it.
#
# Both downloads are pinned and checked. To update, change the versions and hashes below:
#   gh release view -R shinchiro/mpv-winbuild-cmake --json tagName,assets
#   https://vulkan.lunarg.com/sdk/files.json (windows > VulkanRT-X64-*-Components.zip)
#
# Runs anywhere with bash, curl and 7z (Git Bash on GitHub's Windows runners has all three);
# without 7z it uses bsdtar, as macOS's tar is.
set -euo pipefail

MPV_RELEASE="20261111"
MPV_ASSET="mpv-dev-x86_64-20261111-git-bcde6b93cc.7z"
MPV_SHA256="273c079b11ea5575822f972756f4c9a71149a5c10130353abeb8586738938452"
VULKAN_VERSION="1.4.363.0"
VULKAN_SHA256="a25a927aa8b9f0371048f1861cf88ac3b9bc9b1fb332c42d897c8ab32695769a"

cd "$(dirname "$0")/../.."
out="desktop/mpv-bundle/windows-x64"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

download() { # url, file, expected sha256
  echo "Downloading $1"
  curl -fsSL --retry 3 -o "$2" "$1"
  local actual
  actual="$(sha256 "$2")"
  if [ "$actual" != "$3" ]; then
    echo "Checksum mismatch for $2: expected $3, got $actual" >&2
    exit 1
  fi
}

extract() { # archive, destination
  mkdir -p "$2"
  if command -v 7z >/dev/null; then
    7z x -y -bso0 -o"$2" "$1"
  else
    tar -xf "$1" -C "$2"
  fi
}

download "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$MPV_RELEASE/$MPV_ASSET" \
  "$work/mpv.7z" "$MPV_SHA256"
download "https://sdk.lunarg.com/sdk/download/$VULKAN_VERSION/windows/VulkanRT-X64-$VULKAN_VERSION-Components.zip" \
  "$work/vulkan.zip" "$VULKAN_SHA256"
extract "$work/mpv.7z" "$work/mpv"
extract "$work/vulkan.zip" "$work/vulkan"

rm -rf "$out"
mkdir -p "$out/licenses"
cp "$work/mpv/libmpv-2.dll" "$out/"
cp "$work/vulkan/VulkanRT-X64-$VULKAN_VERSION-Components/x64/vulkan-1.dll" "$out/"
cp "$work/vulkan/VulkanRT-X64-$VULKAN_VERSION-Components/VulkanRT-License.txt" "$out/licenses/Vulkan-Loader.txt"

mpv_commit="${MPV_ASSET##*-git-}"
mpv_commit="${mpv_commit%.7z}"
cat > "$out/THIRD_PARTY_NOTICES.txt" <<EOF
Third-party libraries bundled with Aperture for Windows

libmpv-2.dll
  mpv (https://mpv.io) with FFmpeg and the libraries it uses linked in, built by
  shinchiro/mpv-winbuild-cmake, release $MPV_RELEASE (mpv commit $mpv_commit).
  mpv and FFmpeg are built with their GPL options (x264 among others), so this file is
  distributed under the GNU GPL, version 2 or later; Aperture uses it under version 3, its
  own licence (LICENSE). Build recipe and the exact source of every component:
  https://github.com/shinchiro/mpv-winbuild-cmake (packages/*.cmake at that release).
  https://github.com/mpv-player/mpv/tree/$mpv_commit
  See docs/DESKTOP.md for how releases provide the source.

vulkan-1.dll
  Khronos Vulkan loader from the LunarG Vulkan Runtime $VULKAN_VERSION, Apache License 2.0
  (licenses/Vulkan-Loader.txt). Source: https://github.com/KhronosGroup/Vulkan-Loader
EOF

echo "Put libmpv-2.dll and vulkan-1.dll into $out"
