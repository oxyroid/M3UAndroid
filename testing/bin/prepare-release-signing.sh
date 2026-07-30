#!/usr/bin/env bash

set -euo pipefail

readonly PUBLIC_APP_CERT_SHA256="49545adef6b5b670f35249e02ead730971f2f8b4b9fedde1f8faf6b499edd747"

usage() {
  cat >&2 <<'EOF'
Usage:
  prepare-release-signing.sh \
    [--test-app-expected-cert-sha256 <sha256>] \
    <github-event-name>

pull_request does not read or export signing material. Every other event is
treated as a publishing event and requires both the app and reference-extension
keystores.

--test-app-expected-cert-sha256 is only for the repository's temporary-key
contract test. Production workflows must not pass it; without it, the app
certificate is pinned to the public production certificate built into this
script.
EOF
}

fail() {
  echo "Release signing preparation failed: $*" >&2
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

decode_keystore() {
  local encoded="$1"
  local destination="$2"

  if ! printf '%s' "$encoded" | python3 -c '
import base64
import pathlib
import sys

destination = pathlib.Path(sys.argv[1])
try:
    encoded = b"".join(sys.stdin.buffer.read().split())
    decoded = base64.b64decode(encoded, validate=True)
except Exception:
    raise SystemExit(1)
if not decoded:
    raise SystemExit(1)
destination.write_bytes(decoded)
' "$destination"; then
    fail "a keystore is not valid non-empty base64"
  fi
}

keystore_certificate_sha256() {
  local keystore="$1"
  local store_password_environment_variable="$2"
  local alias="$3"
  local certificate_file="$4"

  if ! keytool \
    -exportcert \
    -noprompt \
    -keystore "$keystore" \
    -storepass:env "$store_password_environment_variable" \
    -alias "$alias" \
    -file "$certificate_file" \
    >/dev/null 2>&1; then
    fail "cannot read signing alias '$alias' from a decoded keystore"
  fi

  python3 - "$certificate_file" <<'PY'
import hashlib
import pathlib
import sys

print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
}

validate_private_key() {
  local keystore="$1"
  local store_password_environment_variable="$2"
  local key_password_environment_variable="$3"
  local alias="$4"
  local unsigned_jar="$5"
  local signed_jar="$6"

  if ! jarsigner \
    -keystore "$keystore" \
    -storepass:env "$store_password_environment_variable" \
    -keypass:env "$key_password_environment_variable" \
    -signedjar "$signed_jar" \
    "$unsigned_jar" \
    "$alias" \
    >/dev/null 2>&1; then
    fail "cannot unlock private signing key '$alias' with the supplied key password"
  fi
}

github_environment_entry() {
  local destination="$1"
  local name="$2"
  local value="$3"
  local delimiter="M3U_SIGNING_${$}_${RANDOM}_1"

  while printf '%s\n' "$value" | grep -Fqx "$delimiter"; do
    delimiter="${delimiter}x"
  done
  {
    printf '%s<<%s\n' "$name" "$delimiter"
    printf '%s\n' "$value"
    printf '%s\n' "$delimiter"
  } >> "$destination"
}

test_app_expected_cert_sha256=""
event_name=""
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
      if [[ -n "$event_name" ]]; then
        usage
        exit 2
      fi
      event_name="$1"
      shift
      ;;
  esac
done

if [[ -z "$event_name" ]]; then
  usage
  exit 2
fi

# Pull requests deliberately receive no Gradle signing properties, even when a
# same-repository PR could technically access signing secrets.
if [[ "$event_name" == "pull_request" ]]; then
  echo "Pull-request Release APKs will remain unsigned."
  exit 0
fi

required_variables=(
  M3U_APP_RELEASE_KEYSTORE_BASE64
  M3U_APP_RELEASE_STORE_PASSWORD
  M3U_APP_RELEASE_KEY_ALIAS
  M3U_APP_RELEASE_KEY_PASSWORD
  M3U_REFERENCE_EXTENSION_RELEASE_KEYSTORE_BASE64
  M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD
  M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS
  M3U_REFERENCE_EXTENSION_RELEASE_KEY_PASSWORD
  M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256
)
missing_variables=()
for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name-}" ]]; then
    missing_variables+=("$variable_name")
  fi
done
if (( ${#missing_variables[@]} > 0 )); then
  fail "publishing event '$event_name' is missing: ${missing_variables[*]}"
fi

command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v keytool >/dev/null 2>&1 || fail "keytool is required"
command -v jarsigner >/dev/null 2>&1 || fail "jarsigner is required"

runner_temp="${RUNNER_TEMP-}"
github_env="${GITHUB_ENV-}"
[[ -n "$runner_temp" ]] || fail "RUNNER_TEMP is required for publishing events"
[[ "$runner_temp" == /* ]] || fail "RUNNER_TEMP must be an absolute path"
[[ -d "$runner_temp" ]] || fail "RUNNER_TEMP does not exist"
[[ -n "$github_env" ]] || fail "GITHUB_ENV is required for publishing events"

app_expected_cert_sha256="$PUBLIC_APP_CERT_SHA256"
if [[ -n "$test_app_expected_cert_sha256" ]]; then
  app_expected_cert_sha256="$(normalize_sha256 "$test_app_expected_cert_sha256")"
  require_sha256 "test app certificate" "$app_expected_cert_sha256"
  echo "Using the explicit temporary-key app certificate override for contract testing."
fi
reference_expected_cert_sha256="$(
  normalize_sha256 "$M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256"
)"
require_sha256 "reference extension certificate" "$reference_expected_cert_sha256"
if [[ "$reference_expected_cert_sha256" == "$app_expected_cert_sha256" ]]; then
  fail "the reference extension must use a certificate independent from the app"
fi

umask 077
signing_directory="$(mktemp -d "$runner_temp/m3u-release-signing.XXXXXX")"
preparation_complete=0
cleanup_signing_directory_on_failure() {
  local exit_status=$?

  if (( preparation_complete == 0 )); then
    case "$signing_directory" in
      "$runner_temp"/m3u-release-signing.*)
        rm -rf -- "$signing_directory"
        ;;
      *)
        echo "Refusing to clean unexpected signing directory: $signing_directory" >&2
        ;;
    esac
  fi
  return "$exit_status"
}
trap cleanup_signing_directory_on_failure EXIT

app_keystore="$signing_directory/app-release.keystore"
reference_keystore="$signing_directory/reference-extension-release.keystore"
decode_keystore "$M3U_APP_RELEASE_KEYSTORE_BASE64" "$app_keystore"
decode_keystore \
  "$M3U_REFERENCE_EXTENSION_RELEASE_KEYSTORE_BASE64" \
  "$reference_keystore"

private_key_probe="$signing_directory/private-key-probe.jar"
python3 - "$private_key_probe" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "w") as archive:
    archive.writestr("m3u-release-signing-probe", b"private-key-entry")
PY
validate_private_key \
  "$app_keystore" \
  M3U_APP_RELEASE_STORE_PASSWORD \
  M3U_APP_RELEASE_KEY_PASSWORD \
  "$M3U_APP_RELEASE_KEY_ALIAS" \
  "$private_key_probe" \
  "$signing_directory/app-private-key-probe.jar"
validate_private_key \
  "$reference_keystore" \
  M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD \
  M3U_REFERENCE_EXTENSION_RELEASE_KEY_PASSWORD \
  "$M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS" \
  "$private_key_probe" \
  "$signing_directory/reference-extension-private-key-probe.jar"

app_actual_cert_sha256="$(
  keystore_certificate_sha256 \
    "$app_keystore" \
    M3U_APP_RELEASE_STORE_PASSWORD \
    "$M3U_APP_RELEASE_KEY_ALIAS" \
    "$signing_directory/app-certificate.der"
)"
if [[ "$app_actual_cert_sha256" != "$app_expected_cert_sha256" ]]; then
  fail "app keystore certificate does not match the pinned public app certificate"
fi

reference_actual_cert_sha256="$(
  keystore_certificate_sha256 \
    "$reference_keystore" \
    M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD \
    "$M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS" \
    "$signing_directory/reference-extension-certificate.der"
)"
if [[ "$reference_actual_cert_sha256" != "$reference_expected_cert_sha256" ]]; then
  fail "reference-extension keystore certificate does not match its expected certificate"
fi

environment_block="$signing_directory/github-environment"
: > "$environment_block"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uReleaseSigningRequired \
  true
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uAppReleaseStoreFile \
  "$app_keystore"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uAppReleaseStorePassword \
  "$M3U_APP_RELEASE_STORE_PASSWORD"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uAppReleaseKeyAlias \
  "$M3U_APP_RELEASE_KEY_ALIAS"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uAppReleaseKeyPassword \
  "$M3U_APP_RELEASE_KEY_PASSWORD"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile \
  "$reference_keystore"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStorePassword \
  "$M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyAlias \
  "$M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS"
github_environment_entry \
  "$environment_block" \
  ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyPassword \
  "$M3U_REFERENCE_EXTENSION_RELEASE_KEY_PASSWORD"
github_environment_entry \
  "$environment_block" \
  M3U_APP_EXPECTED_CERT_SHA256 \
  "$app_expected_cert_sha256"
github_environment_entry \
  "$environment_block" \
  M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256 \
  "$reference_expected_cert_sha256"

cat "$environment_block" >> "$github_env"
rm -f -- \
  "$environment_block" \
  "$private_key_probe" \
  "$signing_directory/app-private-key-probe.jar" \
  "$signing_directory/reference-extension-private-key-probe.jar" \
  "$signing_directory/app-certificate.der" \
  "$signing_directory/reference-extension-certificate.der"
preparation_complete=1
echo "Release signing material passed certificate pinning and was prepared."
