#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sample_task="${1:-assembleDebug}"
sdk_version="$(awk -F= '/^extensionSdkVersion=/{print $2; exit}' \
  "$repository_root/gradle.properties")"
if [[ -z "$sdk_version" ]]; then
  echo "extensionSdkVersion is missing from gradle.properties" >&2
  exit 1
fi
sdk_archive="$repository_root/build/distributions/m3u-extension-sdk-$sdk_version.zip"
sdk_extract_root="$(mktemp -d)"

cleanup() {
  if [[ -n "$sdk_extract_root" && -d "$sdk_extract_root" ]]; then
    rm -rf -- "$sdk_extract_root"
  fi
}
trap cleanup EXIT

if [[ "$sample_task" != "assembleDebug" && "$sample_task" != "installDebug" ]]; then
  echo "Usage: $0 [assembleDebug|installDebug]" >&2
  exit 2
fi

if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  android_sdk="$(awk -F= '/^sdk.dir=/{sub(/^sdk.dir=/, ""); print; exit}' \
    "$repository_root/local.properties" 2>/dev/null || true)"
  if [[ -z "$android_sdk" ]]; then
    echo "ANDROID_HOME is not set and sdk.dir is missing from the root local.properties" >&2
    exit 1
  fi
  export ANDROID_HOME="$android_sdk"
fi

"$repository_root/gradlew" \
  --project-dir "$repository_root" \
  verifyExtensionSdkBundle

if [[ ! -f "$sdk_archive" ]]; then
  echo "Extension SDK archive was not produced at $sdk_archive" >&2
  exit 1
fi
unzip -q "$sdk_archive" -d "$sdk_extract_root"
sdk_repository="$sdk_extract_root/repository"

"$repository_root/gradlew" \
  --project-dir "$repository_root/samples/hello-extension" \
  clean \
  "$sample_task" \
  "-PextensionSdkRepository=$sdk_repository" \
  --refresh-dependencies
