#!/usr/bin/env python3
"""Stage a deterministic transitive ELF shared-library closure for Android."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

NEEDED_RE = re.compile(r"Shared library: \[(?P<name>[^\]]+)\]")
DEFAULT_SYSTEM_LIBS = {
    "libandroid.so", "libc.so", "libdl.so", "libEGL.so", "libGLESv2.so",
    "libGLESv3.so", "libjnigraphics.so", "liblog.so", "libm.so", "libmediandk.so",
    "libOpenSLES.so", "libvulkan.so", "libz.so",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def needed(path: Path, readelf: str) -> list[str]:
    result = subprocess.run(
        [readelf, "-d", str(path)], check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    return NEEDED_RE.findall(result.stdout)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lib-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--root", action="append", required=True)
    parser.add_argument("--readelf", default="readelf")
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--system-lib", action="append", default=[])
    args = parser.parse_args()

    lib_dir = args.lib_dir.resolve()
    output = args.output.resolve()
    system_libs = DEFAULT_SYSTEM_LIBS | set(args.system_lib)
    if not lib_dir.is_dir():
        raise SystemExit(f"Library directory does not exist: {lib_dir}")

    queue = list(dict.fromkeys(args.root))
    closure: dict[str, list[str]] = {}
    missing: set[str] = set()
    while queue:
        name = queue.pop(0)
        if name in closure or name in system_libs:
            continue
        path = lib_dir / name
        if not path.is_file():
            missing.add(name)
            continue
        dependencies = needed(path, args.readelf)
        closure[name] = dependencies
        for dependency in dependencies:
            if dependency not in closure and dependency not in system_libs:
                queue.append(dependency)

    if missing:
        raise SystemExit("Missing non-system shared libraries: " + ", ".join(sorted(missing)))

    output.mkdir(parents=True, exist_ok=True)
    for old in output.glob("*.so"):
        old.unlink()
    records = []
    for name in sorted(closure):
        source = lib_dir / name
        target = output / name
        shutil.copy2(source, target)
        records.append({
            "name": name,
            "bytes": target.stat().st_size,
            "sha256": sha256(target),
            "needed": closure[name],
        })

    manifest = {
        "schema": "android-native-closure/1",
        "roots": list(dict.fromkeys(args.root)),
        "systemLibraries": sorted(system_libs),
        "libraryCount": len(records),
        "totalBytes": sum(item["bytes"] for item in records),
        "libraries": records,
    }
    manifest_path = args.manifest or output / "NATIVE_CLOSURE.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Staged {len(records)} libraries ({manifest['totalBytes']} bytes) into {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
