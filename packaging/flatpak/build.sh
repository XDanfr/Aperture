#!/usr/bin/env bash
# Builds the Aperture Flatpak and a single-file bundle of it.
#
#   packaging/flatpak/build.sh [--install]
#
# Needs flatpak and flatpak-builder, with Flathub added as a remote
# (flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo).
# Builds the Linux app first unless desktop/build/dist already has its tarball. The first build
# compiles FFmpeg and mpv, which takes a while; later builds reuse them.
#
# Output: desktop/build/dist/Aperture-<version>-linux-<arch>.flatpak. --install also installs it
# for the current user (flatpak run cc.xdan.Aperture).
set -euo pipefail

cd "$(dirname "$0")/../.."
version="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' desktop/build.gradle.kts)"
arch="$(uname -m | sed 's/x86_64/x64/; s/aarch64/arm64/')"
tarball="desktop/build/dist/Aperture-$version-linux-$arch.tar.gz"

[ -f "$tarball" ] || tools/desktop/package_linux.sh
cp "$tarball" packaging/flatpak/Aperture-linux.tar.gz

builder_args=(--force-clean --install-deps-from=flathub --repo=desktop/build/flatpak-repo
  --state-dir=desktop/build/flatpak-builder)
[ "${1:-}" = "--install" ] && builder_args+=(--user --install)
flatpak-builder --user "${builder_args[@]}" desktop/build/flatpak-app packaging/flatpak/cc.xdan.Aperture.yml

flatpak build-bundle desktop/build/flatpak-repo "desktop/build/dist/Aperture-$version-linux-$arch.flatpak" \
  cc.xdan.Aperture --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
echo "Built desktop/build/dist/Aperture-$version-linux-$arch.flatpak"
