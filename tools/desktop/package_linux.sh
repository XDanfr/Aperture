#!/usr/bin/env bash
# Builds the Linux packages: .deb and .rpm (which depend on the distribution's libmpv) and a
# portable tarball of the app, which the aperture-bin AUR package and the Flatpak use.
#
#   tools/desktop/package_linux.sh
#
# Needs JDK 21 (Gradle provisions it), plus dpkg-deb and fakeroot for the .deb and rpmbuild for
# the .rpm. On Debian/Ubuntu: sudo apt install fakeroot rpm. Output: desktop/build/dist/.
set -euo pipefail

cd "$(dirname "$0")/../.."
version="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' desktop/build.gradle.kts)"
dist="desktop/build/dist"
mkdir -p "$dist"

# Linux packages never bundle libmpv; make sure a stray local copy can't sneak in.
rm -rf desktop/mpv-bundle/linux desktop/mpv-bundle/linux-x64 desktop/mpv-bundle/linux-arm64

formats=(:desktop:createDistributable :desktop:packageDeb)
command -v rpmbuild >/dev/null && formats+=(:desktop:packageRpm) || echo "rpmbuild not found; skipping the .rpm" >&2
./gradlew --no-configuration-cache "${formats[@]}"

binaries="desktop/build/compose/binaries/main"
cp "$binaries"/deb/*.deb "$dist/"

# jpackage's maintainer scripts register the menu entry with xdg-desktop-menu under `set -e`, so
# on systems without a desktop menu directory (servers, WSL, containers) the whole install fails.
# A missing menu entry shouldn't block installing; repack the .deb with those calls made optional.
for deb in "$dist"/*.deb; do
  work="$(mktemp -d)"
  fakeroot sh -c "
    dpkg-deb -R '$deb' '$work/pkg' &&
    sed -i -E 's/^(xdg-[a-z-]+ .*[^ ])$/\1 || true/' '$work/pkg/DEBIAN/postinst' '$work/pkg/DEBIAN/prerm' &&
    dpkg-deb -Zxz -b '$work/pkg' '$deb' >/dev/null"
  rm -rf "$work"
done
if compgen -G "$binaries/rpm/*.rpm" >/dev/null; then cp "$binaries"/rpm/*.rpm "$dist/"; fi

arch="$(uname -m | sed 's/x86_64/x64/; s/aarch64/arm64/')"
tar -C "$binaries/app" -czf "$dist/Aperture-$version-linux-$arch.tar.gz" aperture
ls -l "$dist"
