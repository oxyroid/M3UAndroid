#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -lt 2 ]]; then
  echo "Usage: $0 <debug|release> <smartphone-apk> [...]" >&2
  exit 2
fi

build_variant="$1"
shift

if [[ "$build_variant" != "debug" && "$build_variant" != "release" ]]; then
  echo "Unsupported build variant: $build_variant" >&2
  exit 2
fi

for smartphone_apk in "$@"; do
  if [[ ! -f "$smartphone_apk" ]]; then
    echo "Smartphone APK was not found: $smartphone_apk" >&2
    exit 2
  fi

  python3 - "$build_variant" "$smartphone_apk" <<'PY'
import hashlib
import json
import pathlib
import re
import sys
import zipfile

variant = sys.argv[1]
apk_path = pathlib.Path(sys.argv[2])
asset_prefix = "assets/default-library/"
manifest_path = f"{asset_prefix}manifest.json"
bootstrap_markers = {
    "manifest path": b"default-library/manifest.json",
    "unique work name": b"debug-default-library-bootstrap",
    "worker type": b"DebugDefaultLibraryWorker",
}
sample_markers = {
    "Apple sample origin": b"devstreaming-cdn.apple.com",
    "Mux sample origin": b"test-streams.mux.dev",
    "Blender sample origin": b"download.blender.org",
    "Apple sample channel": b"apple.bipbop.avc",
}

with zipfile.ZipFile(apk_path) as apk:
    packaged_files = [
        entry
        for entry in apk.infolist()
        if not entry.is_dir()
    ]
    default_library_entries = sorted(
        entry.filename
        for entry in packaged_files
        if entry.filename.startswith(asset_prefix)
    )

    if variant == "release":
        if default_library_entries:
            raise SystemExit(
                "Release APK contains debug default-library assets: "
                + ", ".join(default_library_entries)
            )
        forbidden_hits = {}
        release_markers = {**bootstrap_markers, **sample_markers}
        for entry in packaged_files:
            packaged_bytes = apk.read(entry)
            for label, marker in release_markers.items():
                if marker in packaged_bytes:
                    forbidden_hits.setdefault(label, []).append(entry.filename)
        if forbidden_hits:
            details = "; ".join(
                f"{label}: {', '.join(entries)}"
                for label, entries in sorted(forbidden_hits.items())
            )
            raise SystemExit(
                "Release APK contains debug bootstrap code or sample data: "
                + details
            )
        print(
            "Verified release APK has no default-library code or data: "
            f"{apk_path}"
        )
        sys.exit(0)

    if manifest_path not in default_library_entries:
        raise SystemExit(
            f"Debug APK does not contain {manifest_path}: {apk_path}"
        )
    dex_bytes = b"".join(
        apk.read(entry)
        for entry in packaged_files
        if entry.filename.startswith("classes") and entry.filename.endswith(".dex")
    )
    missing_bootstrap_markers = [
        label
        for label, marker in bootstrap_markers.items()
        if marker not in dex_bytes
    ]
    if missing_bootstrap_markers:
        raise SystemExit(
            "Debug APK does not contain its default-library bootstrap code: "
            + ", ".join(missing_bootstrap_markers)
        )

    manifest = json.loads(apk.read(manifest_path))
    playlist_asset = manifest.get("playlistAsset")
    if not isinstance(playlist_asset, str):
        raise SystemExit("Debug default-library manifest has no playlistAsset")
    packaged_playlist_path = f"assets/{playlist_asset}"
    if packaged_playlist_path not in default_library_entries:
        raise SystemExit(
            "Debug APK does not contain its declared playlist asset: "
            + packaged_playlist_path
        )

    playlist_bytes = apk.read(packaged_playlist_path)
    actual_sha256 = hashlib.sha256(playlist_bytes).hexdigest()
    if actual_sha256 != manifest.get("playlistSha256"):
        raise SystemExit(
            "Packaged default-library playlist SHA-256 does not match its manifest"
        )

    playlist_text = playlist_bytes.decode("utf-8")
    packaged_channel_ids = re.findall(r'\btvg-id="([^"]+)"', playlist_text)
    expected_channel_ids = manifest.get("expectedChannelIds")
    if (
        not isinstance(expected_channel_ids, list)
        or len(packaged_channel_ids) != len(set(packaged_channel_ids))
        or set(packaged_channel_ids) != set(expected_channel_ids)
    ):
        raise SystemExit(
            "Packaged default-library channels do not match their manifest"
        )

    print(
        "Verified debug default-library assets: "
        f"{apk_path} ({len(packaged_channel_ids)} channels)"
    )
PY
done
