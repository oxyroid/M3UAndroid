#!/usr/bin/env bash

set -euo pipefail

readonly PUBLIC_APP_CERT_SHA256="49545adef6b5b670f35249e02ead730971f2f8b4b9fedde1f8faf6b499edd747"

usage() {
  cat >&2 <<'EOF'
Usage:
  verify-release-apk-signing.sh \
    [--test-app-expected-cert-sha256 <sha256>] \
    <smartphone-apk> <tv-apk> <reference-extension-apk>

M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256 must contain the independently
pinned reference-extension certificate.

--test-app-expected-cert-sha256 is only for the repository's temporary-key
contract test. Production workflows must not pass it; without it, app APKs are
verified against the public production certificate built into this script.
EOF
}

fail() {
  echo "Release APK signing verification failed: $*" >&2
  exit 1
}

normalize_sha256() {
  printf '%s' "$1" \
    | tr -d ':' \
    | tr '[:upper:]' '[:lower:]'
}

require_sha256() {
  local label="$1"
  local digest="$2"

  if [[ ! "$digest" =~ ^[0-9a-f]{64}$ ]]; then
    fail "$label must be a 64-character SHA-256 digest"
  fi
}

find_apksigner() {
  if [[ -n "${M3U_APKSIGNER-}" ]]; then
    [[ -x "$M3U_APKSIGNER" ]] || fail "M3U_APKSIGNER is not executable"
    printf '%s\n' "$M3U_APKSIGNER"
    return
  fi
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return
  fi

  local android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$android_sdk_root" ]]; then
    local repository_root
    repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
    if [[ -f "$repository_root/local.properties" ]]; then
      android_sdk_root="$(
        sed -n 's/^sdk[.]dir=//p' "$repository_root/local.properties" \
          | head -n 1 \
          | sed 's/\\:/:/g; s/\\\\/\\/g'
      )"
    fi
  fi
  [[ -n "$android_sdk_root" ]] || fail "Android SDK location is unavailable"
  [[ -d "$android_sdk_root/build-tools" ]] || fail "Android build-tools are unavailable"

  local candidate
  candidate="$(
    find "$android_sdk_root/build-tools" -type f -name apksigner -perm -u+x \
      | sort \
      | tail -n 1
  )"
  [[ -n "$candidate" ]] || fail "apksigner was not found in Android build-tools"
  printf '%s\n' "$candidate"
}

require_output_path() {
  local label="$1"
  local apk="$2"
  local expected_directory="$3"
  local actual_directory

  actual_directory="$(dirname "$apk")"

  case "$actual_directory" in
    "$expected_directory"|*/"$expected_directory")
      ;;
    *)
      fail "$label APK is outside $expected_directory"
      ;;
  esac
  [[ "$apk" == *.apk ]] || fail "$label artifact is not an APK"
  [[ -f "$apk" ]] || fail "$label APK does not exist: $apk"
}

apk_certificate_sha256() {
  local label="$1"
  local apk="$2"
  local output

  if ! output="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
    fail "$label APK does not have a valid Android signature"
  fi

  local digests
  digests="$(
    printf '%s\n' "$output" \
      | sed -nE \
        's/^.*Signer( #[0-9]+|:)[[:space:]]+certificate SHA-256 digest:[[:space:]]*([0-9A-Fa-f:]+)[[:space:]]*$/\2/p'
  )"
  local digest_count
  digest_count="$(
    printf '%s\n' "$digests" \
      | sed '/^$/d' \
      | wc -l \
      | tr -d ' '
  )"
  if [[ "$digest_count" != "1" ]]; then
    echo "apksigner output for $label:" >&2
    printf '%s\n' "$output" >&2
    fail "$label APK must have exactly one signer certificate"
  fi
  normalize_sha256 "$digests"
}

test_app_expected_cert_sha256=""
while (( $# > 0 )); do
  case "$1" in
    --test-app-expected-cert-sha256)
      (( $# >= 2 )) || {
        usage
        exit 2
      }
      test_app_expected_cert_sha256="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      usage
      exit 2
      ;;
    *)
      break
      ;;
  esac
done

if (( $# != 3 )); then
  usage
  exit 2
fi

smartphone_apk="$1"
tv_apk="$2"
reference_apk="$3"
reference_expected_cert_sha256="$(
  normalize_sha256 "${M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256-}"
)"
require_sha256 "reference extension certificate" "$reference_expected_cert_sha256"

app_expected_cert_sha256="$PUBLIC_APP_CERT_SHA256"
if [[ -n "$test_app_expected_cert_sha256" ]]; then
  app_expected_cert_sha256="$(normalize_sha256 "$test_app_expected_cert_sha256")"
  require_sha256 "test app certificate" "$app_expected_cert_sha256"
  echo "Using the explicit temporary-key app certificate override for contract testing."
fi
if [[ "$reference_expected_cert_sha256" == "$app_expected_cert_sha256" ]]; then
  fail "the reference extension certificate must be independent from the app certificate"
fi

require_output_path \
  smartphone \
  "$smartphone_apk" \
  app/smartphone/build/outputs/published-apk/release
require_output_path \
  TV \
  "$tv_apk" \
  app/tv/build/outputs/published-apk/release
require_output_path \
  reference-extension \
  "$reference_apk" \
  testing/extension-reference/build/outputs/apk/release

apksigner="$(find_apksigner)"
smartphone_cert_sha256="$(apk_certificate_sha256 smartphone "$smartphone_apk")"
tv_cert_sha256="$(apk_certificate_sha256 TV "$tv_apk")"
reference_cert_sha256="$(
  apk_certificate_sha256 reference-extension "$reference_apk"
)"

if [[ "$smartphone_cert_sha256" != "$app_expected_cert_sha256" ]]; then
  fail "smartphone APK certificate does not match the pinned app certificate"
fi
if [[ "$tv_cert_sha256" != "$app_expected_cert_sha256" ]]; then
  fail "TV APK certificate does not match the pinned app certificate"
fi
if [[ "$smartphone_cert_sha256" != "$tv_cert_sha256" ]]; then
  fail "smartphone and TV APKs do not share the app certificate"
fi
if [[ "$reference_cert_sha256" != "$reference_expected_cert_sha256" ]]; then
  fail "reference-extension APK certificate does not match its pinned certificate"
fi
if [[ "$reference_cert_sha256" == "$app_expected_cert_sha256" ]]; then
  fail "reference-extension APK unexpectedly shares the app certificate"
fi

echo "Verified Release APK signing identities:"
echo "  app: $app_expected_cert_sha256"
echo "  reference extension: $reference_expected_cert_sha256"
