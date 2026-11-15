#!/usr/bin/env python3
"""Copy libmpv and every library it needs into desktop/mpv-bundle/macos-<arch>/.

Compose copies that folder into Aperture.app/Contents/app/resources, where LibMpv looks first.
Each copied library has its install name and references rewritten to @loader_path, so the set
only ever loads from itself, and is ad-hoc signed (install_name_tool invalidates signatures, and
Apple Silicon refuses to load unsigned code).

The libraries come from Homebrew (`brew install mpv`). Licence files and a notices list are
written alongside; --sources also collects each formula's source archive, for GPL compliance.

    python3 tools/desktop/bundle_libmpv_macos.py [--libmpv PATH] [--out DIR] [--sources DIR]
"""
import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SYSTEM_PREFIXES = ("/usr/lib/", "/System/")
LICENCE_NAMES = re.compile(r"^(COPYING|COPYRIGHT|LICEN[CS]E|NOTICE|AUTHORS)([.-].*)?$", re.I)


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout


def default_arch():
    return {"arm64": "arm64", "x86_64": "x64"}[platform.machine()]


def default_libmpv():
    prefix = run("brew", "--prefix", "mpv").strip()
    return Path(prefix) / "lib" / "libmpv.2.dylib"


def load_commands(binary):
    """(dependencies, rpaths) from otool, as written in the binary."""
    deps = [line.strip().split(" (compatibility")[0]
            for line in run("otool", "-L", str(binary)).splitlines()[1:]]
    install_name = run("otool", "-D", str(binary)).splitlines()[1:]
    deps = [d for d in deps if d not in install_name]
    rpaths, lines = [], run("otool", "-l", str(binary)).splitlines()
    for i, line in enumerate(lines):
        if line.strip() == "cmd LC_RPATH":
            rpaths.append(lines[i + 2].strip().split(" (offset")[0].removeprefix("path "))
    return deps, rpaths


def resolve(reference, binary, rpaths):
    """The file a load command refers to, as dyld would find it from `binary`."""
    loader_dir = str(Path(binary).parent)
    if reference.startswith("@loader_path/"):
        return Path(loader_dir, reference.removeprefix("@loader_path/")).resolve()
    if reference.startswith("@rpath/"):
        rest = reference.removeprefix("@rpath/")
        for rpath in rpaths:
            candidate = Path(rpath.replace("@loader_path", loader_dir), rest)
            if candidate.exists():
                return candidate.resolve()
        # Homebrew keeps sibling libraries together; try next to the one that asked.
        candidate = Path(loader_dir, rest)
        if candidate.exists():
            return candidate.resolve()
        raise SystemExit(f"Can't resolve {reference} from {binary} (rpaths: {rpaths})")
    if reference.startswith("@"):
        raise SystemExit(f"Unsupported reference {reference} in {binary}")
    return Path(reference).resolve()


def collect(libmpv):
    """Every non-system library libmpv loads: {source file: bundled name}."""
    names = {libmpv.resolve(): "libmpv.2.dylib"}
    queue = [libmpv.resolve()]
    while queue:
        binary = queue.pop()
        deps, rpaths = load_commands(binary)
        for reference in deps:
            if reference.startswith(SYSTEM_PREFIXES):
                continue
            source = resolve(reference, binary, rpaths)
            if source not in names:
                # Keep the name the reference used (libavcodec.62.dylib, not the versioned file).
                names[source] = Path(reference).name
                queue.append(source)
    clashes = {n for n in names.values() if list(names.values()).count(n) > 1}
    if clashes:
        raise SystemExit(f"Two libraries would share a name: {clashes}")
    return names


def rewrite(out, names):
    by_reference_name = set(names.values())
    for name in sorted(by_reference_name):
        target = out / name
        deps, rpaths = load_commands(target)
        args = ["install_name_tool", "-id", f"@rpath/{name}"]
        for reference in deps:
            if reference.startswith(SYSTEM_PREFIXES):
                continue
            dep_name = Path(reference).name
            if dep_name not in by_reference_name:
                raise SystemExit(f"{name} needs {reference}, which wasn't bundled")
            args += ["-change", reference, f"@loader_path/{dep_name}"]
        for rpath in rpaths:
            args += ["-delete_rpath", rpath]
        subprocess.run(args + [str(target)], check=True, capture_output=True)
        subprocess.run(["codesign", "--force", "--sign", "-", "--timestamp=none", str(target)],
                       check=True, capture_output=True)


def verify(out):
    """Nothing may point outside the bundle or the OS."""
    problems = []
    for lib in sorted(out.glob("*.dylib")):
        deps, rpaths = load_commands(lib)
        for reference in deps:
            if not reference.startswith(SYSTEM_PREFIXES + ("@loader_path/",)):
                problems.append(f"{lib.name} -> {reference}")
            elif reference.startswith("@loader_path/") and not (out / Path(reference).name).exists():
                problems.append(f"{lib.name} -> missing {reference}")
        if rpaths:
            problems.append(f"{lib.name} keeps rpaths {rpaths}")
        if subprocess.run(["codesign", "--verify", "--strict", str(lib)], capture_output=True).returncode:
            problems.append(f"{lib.name} signature invalid")
    if problems:
        raise SystemExit("Bundle check failed:\n  " + "\n  ".join(problems))


def minimum_macos(out):
    """The newest LC_BUILD_VERSION minos among the libraries: the oldest macOS the set runs on."""
    def parse(version):
        return tuple(int(part) for part in version.split("."))
    found = []
    for lib in out.glob("*.dylib"):
        lines = run("otool", "-l", str(lib)).splitlines()
        for i, line in enumerate(lines):
            if line.strip() in ("cmd LC_BUILD_VERSION", "cmd LC_VERSION_MIN_MACOSX"):
                for follow in lines[i + 1:i + 6]:
                    key, _, value = follow.strip().partition(" ")
                    if key in ("minos", "version"):
                        found.append(value.strip())
                        break
    return max(found, key=parse) if found else None


def formula_of(path):
    """Homebrew formula and version from a Cellar path."""
    match = re.search(r"/Cellar/([^/]+)/([^/]+)/", str(path))
    return (match.group(1), match.group(2)) if match else (None, None)


def write_notices(out, names, sources_dir):
    formulae = {}
    for source, name in names.items():
        formula, version = formula_of(source)
        if formula:
            formulae.setdefault(formula, {"version": version, "files": []})["files"].append(name)
    info = {f["name"]: f for f in json.loads(run("brew", "info", "--json=v2", *sorted(formulae)))["formulae"]}

    licences = out / "licenses"
    shutil.rmtree(licences, ignore_errors=True)
    lines = [
        "Third-party libraries bundled with Aperture for macOS",
        "",
        "These are Homebrew builds of mpv and the libraries it uses. mpv and FFmpeg are built with",
        "their GPL options, so this set as a whole is distributed under the GNU GPL, version 3 or",
        "later (the same licence as Aperture). Each library's own licence is in licenses/<name>/.",
        "Source code for every library is available from the URL listed, at the version listed;",
        "see docs/DESKTOP.md for how releases provide it.",
        "",
    ]
    for formula in sorted(formulae):
        meta = info.get(formula, {})
        version = formulae[formula]["version"]
        url = (meta.get("urls", {}).get("stable") or {}).get("url", "")
        lines += [
            f"{formula} {version}",
            f"  Licence: {meta.get('license') or 'see licenses/' + formula}",
            f"  Homepage: {meta.get('homepage', '')}",
            f"  Source: {url}",
            f"  Files: {', '.join(sorted(formulae[formula]['files']))}",
            "",
        ]
        keg = Path(run("brew", "--cellar", formula).strip()) / version
        for item in keg.iterdir() if keg.is_dir() else []:
            if item.is_file() and LICENCE_NAMES.match(item.name):
                (licences / formula).mkdir(parents=True, exist_ok=True)
                shutil.copy2(item, licences / formula / item.name)
        if sources_dir:
            subprocess.run(["brew", "fetch", "--quiet", "--build-from-source", formula], check=True)
            cached = Path(run("brew", "--cache", "--build-from-source", formula).strip())
            sources_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cached, sources_dir / f"{formula}-{version}-{cached.name.split('--', 1)[-1]}")
    (out / "THIRD_PARTY_NOTICES.txt").write_text("\n".join(lines))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--libmpv", type=Path, help="libmpv.2.dylib to bundle (default: Homebrew's)")
    parser.add_argument("--out", type=Path, help="output folder (default: desktop/mpv-bundle/macos-<arch>)")
    parser.add_argument("--sources", type=Path, help="also collect each library's source archive here")
    args = parser.parse_args()

    libmpv = args.libmpv or default_libmpv()
    if not libmpv.exists():
        sys.exit(f"{libmpv} not found. Install mpv with Homebrew first: brew install mpv")
    out = args.out or ROOT / "desktop" / "mpv-bundle" / f"macos-{default_arch()}"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)

    names = collect(libmpv)
    for source, name in names.items():
        shutil.copy2(source, out / name)
        os.chmod(out / name, 0o755)
    rewrite(out, names)
    verify(out)
    write_notices(out, names, args.sources)

    # desktop/build.gradle.kts copies this into Info.plist (LSMinimumSystemVersion), so an older
    # Mac shows "needs a newer macOS" instead of a player that can't load.
    minimum = minimum_macos(out)
    (out.parent / f"min-macos-{out.name.removeprefix('macos-')}.txt").write_text(f"{minimum}\n")

    size = sum(f.stat().st_size for f in out.glob("*.dylib")) / 1e6
    print(f"Bundled {len(names)} libraries ({size:.0f} MB) into {out}; needs macOS {minimum} or later")


if __name__ == "__main__":
    main()
