#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
prepare_script="$repository_root/testing/bin/prepare-release-signing.sh"
verify_script="$repository_root/testing/bin/verify-release-apk-signing.sh"
work_directory="$(mktemp -d)"

cleanup() {
  rm -rf -- "$work_directory"
}
trap cleanup EXIT

fail() {
  echo "Release signing contract test failed: $*" >&2
  exit 1
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >"$work_directory/expected-failure.log" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
}

base64_file() {
  python3 - "$1" <<'PY'
import base64
import pathlib
import sys

print(base64.b64encode(pathlib.Path(sys.argv[1]).read_bytes()).decode("ascii"))
PY
}

certificate_sha256() {
  local keystore="$1"
  local password_environment_variable="$2"
  local alias="$3"
  local certificate="$work_directory/certificate-${alias}.der"

  keytool \
    -exportcert \
    -noprompt \
    -keystore "$keystore" \
    -storepass:env "$password_environment_variable" \
    -alias "$alias" \
    -file "$certificate" \
    >/dev/null 2>&1
  python3 - "$certificate" <<'PY'
import hashlib
import pathlib
import sys

print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
}

github_environment_value() {
  local environment_file="$1"
  local requested_name="$2"

  python3 - "$environment_file" "$requested_name" <<'PY'
import pathlib
import sys

lines = pathlib.Path(sys.argv[1]).read_text().splitlines()
requested = sys.argv[2]
values = {}
index = 0
while index < len(lines):
    line = lines[index]
    index += 1
    if "<<" in line:
        name, delimiter = line.split("<<", 1)
        value_lines = []
        while index < len(lines) and lines[index] != delimiter:
            value_lines.append(lines[index])
            index += 1
        if index == len(lines):
            raise SystemExit(f"Unterminated GITHUB_ENV entry: {name}")
        index += 1
        values[name] = "\n".join(value_lines)
    elif "=" in line:
        name, value = line.split("=", 1)
        values[name] = value
if requested not in values:
    raise SystemExit(f"Missing GITHUB_ENV entry: {requested}")
print(values[requested])
PY
}

verify_signing_report() {
  local report="$1"
  local expected_config="$2"
  local expected_app_store="$3"
  local expected_reference_store="$4"
  local expected_app_alias="$5"
  local expected_reference_alias="$6"
  local expected_app_sha256="$7"
  local expected_reference_sha256="$8"

  python3 - \
    "$report" \
    "$expected_config" \
    "$expected_app_store" \
    "$expected_reference_store" \
    "$expected_app_alias" \
    "$expected_reference_alias" \
    "$expected_app_sha256" \
    "$expected_reference_sha256" <<'PY'
import pathlib
import re
import sys

report_path = pathlib.Path(sys.argv[1])
expected_config = sys.argv[2]
expected_stores = {
    ":app:smartphone:signingReport": sys.argv[3],
    ":app:tv:signingReport": sys.argv[3],
    ":testing:extension-reference:signingReport": sys.argv[4],
}
expected_aliases = {
    ":app:smartphone:signingReport": sys.argv[5],
    ":app:tv:signingReport": sys.argv[5],
    ":testing:extension-reference:signingReport": sys.argv[6],
}
expected_sha256 = {
    ":app:smartphone:signingReport": sys.argv[7],
    ":app:tv:signingReport": sys.argv[7],
    ":testing:extension-reference:signingReport": sys.argv[8],
}
sections = {}
current_task = None
for line in report_path.read_text().splitlines():
    match = re.match(r"> Task (:[^ ]+:signingReport)(?: .*)?$", line)
    if match:
        current_task = match.group(1)
        sections[current_task] = []
    elif current_task is not None:
        sections[current_task].append(line)

for task, expected_store in expected_stores.items():
    if task not in sections:
        raise SystemExit(f"Missing signingReport output for {task}")
    lines = sections[task]
    try:
        release_index = lines.index("Variant: release")
    except ValueError:
        raise SystemExit(f"Missing release variant in {task}")
    fields = {}
    for line in lines[release_index + 1 :]:
        if line == "----------" or line.startswith("Variant: "):
            break
        if ": " in line:
            name, value = line.split(": ", 1)
            fields[name] = value
    if fields.get("Config") != expected_config:
        raise SystemExit(
            f"{task} release config was {fields.get('Config')!r}, "
            f"expected {expected_config!r}"
        )
    actual_store = fields.get("Store")
    if actual_store != expected_store:
        raise SystemExit(
            f"{task} release store was {actual_store!r}, "
            f"expected {expected_store!r}"
        )
    actual_alias = fields.get("Alias")
    if actual_alias != expected_aliases[task]:
        raise SystemExit(
            f"{task} release alias was {actual_alias!r}, "
            f"expected {expected_aliases[task]!r}"
        )
    if expected_config == "production":
        actual_sha256 = fields.get("SHA-256", "").replace(":", "").lower()
        if actual_sha256 != expected_sha256[task]:
            raise SystemExit(
                f"{task} release certificate was {actual_sha256!r}, "
                f"expected {expected_sha256[task]!r}"
            )

if (
    expected_config == "production"
    and expected_stores[":app:smartphone:signingReport"]
    == expected_stores[":testing:extension-reference:signingReport"]
):
    raise SystemExit("App and reference extension unexpectedly use the same store")
PY
}

run_signing_report() {
  local report="$1"

  "$repository_root/gradlew" \
    --project-dir "$repository_root" \
    --no-daemon \
    --no-configuration-cache \
    :app:smartphone:signingReport \
    :app:tv:signingReport \
    :testing:extension-reference:signingReport \
    >"$report" \
    2>&1
}

find_apksigner() {
  if [[ -n "${M3U_APKSIGNER-}" ]]; then
    printf '%s\n' "$M3U_APKSIGNER"
    return
  fi
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return
  fi
  local android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$android_sdk_root" && -f "$repository_root/local.properties" ]]; then
    android_sdk_root="$(
      sed -n 's/^sdk[.]dir=//p' "$repository_root/local.properties" \
        | head -n 1 \
        | sed 's/\\:/:/g; s/\\\\/\\/g'
    )"
  fi
  [[ -n "$android_sdk_root" ]] || fail "Android SDK location is unavailable"
  find "$android_sdk_root/build-tools" -type f -name apksigner -perm -u+x \
    | sort \
    | tail -n 1
}

command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v keytool >/dev/null 2>&1 || fail "keytool is required"
command -v jarsigner >/dev/null 2>&1 || fail "jarsigner is required"
apksigner="$(find_apksigner)"
[[ -x "$apksigner" ]] || fail "apksigner is required"
export M3U_APKSIGNER="$apksigner"
if grep -Fq \
  -- '--test-app-expected-cert-sha256' \
  "$repository_root/.github/workflows/android.yml"; then
  fail "the production workflow must not use the test-only app certificate override"
fi

app_keystore="$work_directory/contract-app.p12"
reference_keystore="$work_directory/contract-reference.p12"
app_password="contract-app-password"
reference_password="contract-reference-password"
app_alias="contract-app"
reference_alias="contract-reference"
export CONTRACT_APP_STORE_PASSWORD="$app_password"
export CONTRACT_REFERENCE_STORE_PASSWORD="$reference_password"

keytool \
  -genkeypair \
  -noprompt \
  -storetype PKCS12 \
  -keystore "$app_keystore" \
  -storepass "$app_password" \
  -keypass "$app_password" \
  -alias "$app_alias" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 2 \
  -dname "CN=M3U Release Signing Contract App" \
  >/dev/null 2>&1
keytool \
  -genkeypair \
  -noprompt \
  -storetype PKCS12 \
  -keystore "$reference_keystore" \
  -storepass "$reference_password" \
  -keypass "$reference_password" \
  -alias "$reference_alias" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 2 \
  -dname "CN=M3U Release Signing Contract Reference Extension" \
  >/dev/null 2>&1

app_cert_sha256="$(
  certificate_sha256 \
    "$app_keystore" \
    CONTRACT_APP_STORE_PASSWORD \
    "$app_alias"
)"
reference_cert_sha256="$(
  certificate_sha256 \
    "$reference_keystore" \
    CONTRACT_REFERENCE_STORE_PASSWORD \
    "$reference_alias"
)"
[[ "$app_cert_sha256" != "$reference_cert_sha256" ]] || {
  fail "temporary app and reference-extension certificates are not independent"
}

export M3U_APP_RELEASE_KEYSTORE_BASE64
M3U_APP_RELEASE_KEYSTORE_BASE64="$(base64_file "$app_keystore")"
export M3U_APP_RELEASE_STORE_PASSWORD="$app_password"
export M3U_APP_RELEASE_KEY_ALIAS="$app_alias"
export M3U_APP_RELEASE_KEY_PASSWORD="$app_password"
export M3U_REFERENCE_EXTENSION_RELEASE_KEYSTORE_BASE64
M3U_REFERENCE_EXTENSION_RELEASE_KEYSTORE_BASE64="$(
  base64_file "$reference_keystore"
)"
export M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD="$reference_password"
export M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS="$reference_alias"
export M3U_REFERENCE_EXTENSION_RELEASE_KEY_PASSWORD="$reference_password"
export M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256="$reference_cert_sha256"
export RUNNER_TEMP="$work_directory/runner-temp"
mkdir -p "$RUNNER_TEMP"

# Pull requests must ignore even malformed, present secrets and write no Gradle
# signing properties.
env \
  -u GITHUB_ENV \
  -u RUNNER_TEMP \
  -u M3U_APP_RELEASE_KEYSTORE_BASE64 \
  -u M3U_APP_RELEASE_STORE_PASSWORD \
  -u M3U_APP_RELEASE_KEY_ALIAS \
  -u M3U_APP_RELEASE_KEY_PASSWORD \
  -u M3U_REFERENCE_EXTENSION_RELEASE_KEYSTORE_BASE64 \
  -u M3U_REFERENCE_EXTENSION_RELEASE_STORE_PASSWORD \
  -u M3U_REFERENCE_EXTENSION_RELEASE_KEY_ALIAS \
  -u M3U_REFERENCE_EXTENSION_RELEASE_KEY_PASSWORD \
  -u M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256 \
  "$prepare_script" \
  pull_request \
  >/dev/null
pull_request_environment="$work_directory/pull-request.env"
: > "$pull_request_environment"
(
  export GITHUB_ENV="$pull_request_environment"
  export M3U_APP_RELEASE_KEYSTORE_BASE64="not-base64"
  export M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256="not-a-digest"
  "$prepare_script" pull_request >/dev/null
)
[[ ! -s "$pull_request_environment" ]] || {
  fail "pull_request exported signing properties"
}

# Every piece of key material is mandatory for a publishing event.
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
for missing_variable in "${required_variables[@]}"; do
  missing_environment="$work_directory/missing-${missing_variable}.env"
  : > "$missing_environment"
  expect_failure \
    "push without $missing_variable" \
    env \
    -u "$missing_variable" \
    GITHUB_ENV="$missing_environment" \
    "$prepare_script" \
    push
  [[ ! -s "$missing_environment" ]] || {
    fail "a failed push preparation exported partial signing properties"
  }
done

for publishing_event in workflow_dispatch unexpected_future_event; do
  missing_environment="$work_directory/missing-${publishing_event}.env"
  : > "$missing_environment"
  expect_failure \
    "$publishing_event without app keystore" \
    env \
    -u M3U_APP_RELEASE_KEYSTORE_BASE64 \
    GITHUB_ENV="$missing_environment" \
    "$prepare_script" \
    "$publishing_event"
done

# A present but incorrect key password must fail before any Gradle properties
# are exported. Certificate-only checks are insufficient for signing material.
wrong_key_password_environment="$work_directory/wrong-key-password.env"
: > "$wrong_key_password_environment"
expect_failure \
  "incorrect private-key password" \
  env \
  M3U_APP_RELEASE_KEY_PASSWORD=incorrect-contract-key-password \
  GITHUB_ENV="$wrong_key_password_environment" \
  "$prepare_script" \
  --test-app-expected-cert-sha256 "$app_cert_sha256" \
  workflow_dispatch
[[ ! -s "$wrong_key_password_environment" ]] || {
  fail "a private-key failure exported partial signing properties"
}
if find "$RUNNER_TEMP" -mindepth 1 -print -quit | grep -q .; then
  fail "a private-key failure left decoded signing material in RUNNER_TEMP"
fi

# The production path must reject a random app certificate instead of allowing
# an environment variable to replace the built-in public certificate pin.
pinned_environment="$work_directory/pinned.env"
: > "$pinned_environment"
expect_failure \
  "production app certificate pin" \
  env \
  GITHUB_ENV="$pinned_environment" \
  "$prepare_script" \
  workflow_dispatch
[[ ! -s "$pinned_environment" ]] || {
  fail "a certificate-pin failure exported partial signing properties"
}
if find "$RUNNER_TEMP" -mindepth 1 -print -quit | grep -q .; then
  fail "a certificate-pin failure left decoded signing material in RUNNER_TEMP"
fi

# Gradle itself is the second fail-closed boundary. A caller cannot set
# signing-required while omitting the material and still inspect/build Release.
required_failure_report="$work_directory/required-signing-failure.txt"
if (
  unset \
    ORG_GRADLE_PROJECT_m3uAppReleaseStoreFile \
    ORG_GRADLE_PROJECT_m3uAppReleaseStorePassword \
    ORG_GRADLE_PROJECT_m3uAppReleaseKeyAlias \
    ORG_GRADLE_PROJECT_m3uAppReleaseKeyPassword \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStorePassword \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyAlias \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyPassword
  export ORG_GRADLE_PROJECT_m3uReleaseSigningRequired=true
  run_signing_report "$required_failure_report"
); then
  fail "Gradle accepted required Release signing without signing material"
fi

# With no signing properties, all three Release variants must remain unsigned.
unsigned_signing_report="$work_directory/unsigned-signing-report.txt"
(
  unset \
    ORG_GRADLE_PROJECT_m3uReleaseSigningRequired \
    ORG_GRADLE_PROJECT_m3uAppReleaseStoreFile \
    ORG_GRADLE_PROJECT_m3uAppReleaseStorePassword \
    ORG_GRADLE_PROJECT_m3uAppReleaseKeyAlias \
    ORG_GRADLE_PROJECT_m3uAppReleaseKeyPassword \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStorePassword \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyAlias \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyPassword
  run_signing_report "$unsigned_signing_report"
)
verify_signing_report \
  "$unsigned_signing_report" \
  null \
  null \
  null \
  null \
  null \
  "" \
  ""

# The explicit test-only CLI option permits a temporary app certificate, while
# retaining all other publishing checks.
prepared_environment="$work_directory/prepared.env"
: > "$prepared_environment"
GITHUB_ENV="$prepared_environment" \
  "$prepare_script" \
  --test-app-expected-cert-sha256 "$app_cert_sha256" \
  workflow_dispatch \
  >/dev/null

[[ "$(
  github_environment_value \
    "$prepared_environment" \
    ORG_GRADLE_PROJECT_m3uReleaseSigningRequired
)" == "true" ]] || fail "publishing did not require Release signing"
prepared_app_keystore="$(
  github_environment_value \
    "$prepared_environment" \
    ORG_GRADLE_PROJECT_m3uAppReleaseStoreFile
)"
prepared_reference_keystore="$(
  github_environment_value \
    "$prepared_environment" \
    ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile
)"
case "$prepared_app_keystore" in
  "$RUNNER_TEMP"/*)
    ;;
  *)
    fail "decoded app keystore is outside RUNNER_TEMP"
    ;;
esac
case "$prepared_reference_keystore" in
  "$RUNNER_TEMP"/*)
    ;;
  *)
    fail "decoded reference-extension keystore is outside RUNNER_TEMP"
    ;;
esac
[[ -s "$prepared_app_keystore" ]] || fail "decoded app keystore is missing"
[[ -s "$prepared_reference_keystore" ]] || {
  fail "decoded reference-extension keystore is missing"
}
if [[ "$(
  find "$(dirname "$prepared_app_keystore")" -mindepth 1 -maxdepth 1 -type f \
    | wc -l \
    | tr -d ' '
)" != "2" ]]; then
  fail "successful preparation retained files other than the two keystores"
fi
[[ "$(
  github_environment_value \
    "$prepared_environment" \
    M3U_APP_EXPECTED_CERT_SHA256
)" == "$app_cert_sha256" ]] || fail "prepared app fingerprint is incorrect"
[[ "$(
  github_environment_value \
    "$prepared_environment" \
    M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256
)" == "$reference_cert_sha256" ]] || {
  fail "prepared reference-extension fingerprint is incorrect"
}

# Prove that the exported ORG_GRADLE_PROJECT names drive the real root Gradle
# configuration: phone and TV share production signing, while the reference
# extension uses its separate production store.
signed_signing_report="$work_directory/signed-signing-report.txt"
(
  export ORG_GRADLE_PROJECT_m3uReleaseSigningRequired="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uReleaseSigningRequired
  )"
  export ORG_GRADLE_PROJECT_m3uAppReleaseStoreFile="$prepared_app_keystore"
  export ORG_GRADLE_PROJECT_m3uAppReleaseStorePassword="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uAppReleaseStorePassword
  )"
  export ORG_GRADLE_PROJECT_m3uAppReleaseKeyAlias="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uAppReleaseKeyAlias
  )"
  export ORG_GRADLE_PROJECT_m3uAppReleaseKeyPassword="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uAppReleaseKeyPassword
  )"
  export ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStoreFile
  )"
  export ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStorePassword="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseStorePassword
  )"
  export ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyAlias="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyAlias
  )"
  export ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyPassword="$(
    github_environment_value \
      "$prepared_environment" \
      ORG_GRADLE_PROJECT_m3uReferenceExtensionReleaseKeyPassword
  )"
  run_signing_report "$signed_signing_report"
)
verify_signing_report \
  "$signed_signing_report" \
  production \
  "$prepared_app_keystore" \
  "$prepared_reference_keystore" \
  "$app_alias" \
  "$reference_alias" \
  "$app_cert_sha256" \
  "$reference_cert_sha256"

# Build a tiny valid APK fixture and sign it through apksigner. The signed
# copies live under the same directories consumed by both release workflows.
unsigned_apk="$work_directory/unsigned.apk"
manifest_file="$work_directory/AndroidManifest.xml"
python3 - "$manifest_file" <<'PY'
import pathlib
import sys

pathlib.Path(sys.argv[1]).write_text(
    """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.m3u.testing.signingcontract">
        <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
        <application android:hasCode="false" />
    </manifest>
    """,
    encoding="utf-8",
)
PY
aapt2="$(dirname "$apksigner")/aapt2"
[[ -x "$aapt2" ]] || fail "aapt2 is required next to apksigner"
android_sdk_root="$(cd "$(dirname "$apksigner")/../.." && pwd)"
android_jar="$(
  find "$android_sdk_root/platforms" -type f -name android.jar \
    | sort \
    | tail -n 1
)"
[[ -n "$android_jar" ]] || fail "an Android platform android.jar is required"
"$aapt2" link \
  --manifest "$manifest_file" \
  -I "$android_jar" \
  -o "$unsigned_apk"

smartphone_apk="$work_directory/app/smartphone/build/outputs/published-apk/release/contract.apk"
tv_apk="$work_directory/app/tv/build/outputs/published-apk/release/tv-contract.apk"
reference_apk="$work_directory/testing/extension-reference/build/outputs/apk/release/extension-reference-release.apk"
mkdir -p \
  "$(dirname "$smartphone_apk")" \
  "$(dirname "$tv_apk")" \
  "$(dirname "$reference_apk")"

M3U_CONTRACT_STORE_PASSWORD="$app_password" \
  M3U_CONTRACT_KEY_PASSWORD="$app_password" \
  "$apksigner" sign \
  --min-sdk-version 26 \
  --ks "$app_keystore" \
  --ks-key-alias "$app_alias" \
  --ks-pass env:M3U_CONTRACT_STORE_PASSWORD \
  --key-pass env:M3U_CONTRACT_KEY_PASSWORD \
  --out "$smartphone_apk" \
  "$unsigned_apk"
M3U_CONTRACT_STORE_PASSWORD="$app_password" \
  M3U_CONTRACT_KEY_PASSWORD="$app_password" \
  "$apksigner" sign \
  --min-sdk-version 26 \
  --ks "$app_keystore" \
  --ks-key-alias "$app_alias" \
  --ks-pass env:M3U_CONTRACT_STORE_PASSWORD \
  --key-pass env:M3U_CONTRACT_KEY_PASSWORD \
  --out "$tv_apk" \
  "$unsigned_apk"
M3U_CONTRACT_STORE_PASSWORD="$reference_password" \
  M3U_CONTRACT_KEY_PASSWORD="$reference_password" \
  "$apksigner" sign \
  --min-sdk-version 26 \
  --ks "$reference_keystore" \
  --ks-key-alias "$reference_alias" \
  --ks-pass env:M3U_CONTRACT_STORE_PASSWORD \
  --key-pass env:M3U_CONTRACT_KEY_PASSWORD \
  --out "$reference_apk" \
  "$unsigned_apk"

M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256="$reference_cert_sha256" \
  "$verify_script" \
  --test-app-expected-cert-sha256 "$app_cert_sha256" \
  "$smartphone_apk" \
  "$tv_apk" \
  "$reference_apk" \
  >/dev/null

# The verifier's normal production path remains pinned to the public app
# certificate and therefore rejects the temporary app key.
expect_failure \
  "production APK certificate pin" \
  env \
  M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256="$reference_cert_sha256" \
  "$verify_script" \
  "$smartphone_apk" \
  "$tv_apk" \
  "$reference_apk"

# A valid APK from an unexpected output directory must not satisfy the release
# artifact contract.
unexpected_smartphone_apk="$work_directory/unexpected/smartphone.apk"
mkdir -p "$(dirname "$unexpected_smartphone_apk")"
cp "$smartphone_apk" "$unexpected_smartphone_apk"
expect_failure \
  "unexpected smartphone APK path" \
  env \
  M3U_REFERENCE_EXTENSION_EXPECTED_CERT_SHA256="$reference_cert_sha256" \
  "$verify_script" \
  --test-app-expected-cert-sha256 "$app_cert_sha256" \
  "$unexpected_smartphone_apk" \
  "$tv_apk" \
  "$reference_apk"

echo "Release signing contract verification passed without production keys."
