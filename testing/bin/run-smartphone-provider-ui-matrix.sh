#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <emulator-serial>" >&2
  exit 2
fi

device_serial="$1"
adb_command="${ADB_COMMAND:-adb}"
test_class="com.m3u.testing.SubscriptionSourceSelectionTest"
content_padding_test="com.m3u.testing.SubscriptionContentPaddingTest"
full_test="$test_class,$content_padding_test"
matrix_test="$test_class#providerFormWorksInRequestedAccessibilityConfiguration,$content_padding_test#lastProviderActionCanScrollAboveTheSystemSafeArea"
runner="com.m3u.smartphone.test/androidx.test.runner.AndroidJUnitRunner"
app_package="com.m3u.smartphone"
test_package="com.m3u.smartphone.test"
main_apk="app/smartphone/build/outputs/apk/debug/smartphone-debug.apk"
test_apk="app/smartphone/build/outputs/apk/androidTest/debug/smartphone-debug-androidTest.apk"
work_dir=""
restore_needed=0

adb_for_device() {
  "$adb_command" -s "$device_serial" "$@"
}

read_setting() {
  adb_for_device shell settings get "$1" "$2" | tr -d '\r'
}

restore_setting() {
  local namespace="$1"
  local key="$2"
  local value="$3"

  if [[ -z "$value" || "$value" == "null" ]]; then
    adb_for_device shell settings delete "$namespace" "$key" >/dev/null
  else
    adb_for_device shell settings put "$namespace" "$key" "$value"
  fi
}

apply_original_device_settings() {
  local apply_status=0

  if [[ -n "$original_override_size" ]]; then
    adb_for_device shell wm size "$original_override_size" || apply_status=1
  else
    adb_for_device shell wm size reset || apply_status=1
  fi
  if [[ -n "$original_override_density" ]]; then
    adb_for_device shell wm density "$original_override_density" || apply_status=1
  else
    adb_for_device shell wm density reset || apply_status=1
  fi
  restore_setting system font_scale "$original_font_scale" || apply_status=1
  restore_setting global debug.force_rtl "$original_force_rtl" || apply_status=1
  restore_setting system accelerometer_rotation \
    "$original_accelerometer_rotation" || apply_status=1
  restore_setting system user_rotation "$original_user_rotation" || apply_status=1
  restore_setting secure show_ime_with_hard_keyboard \
    "$original_show_ime" || apply_status=1
  adb_for_device shell setprop debug.force_rtl \
    "$original_force_rtl_property" || apply_status=1

  return "$apply_status"
}

wait_for_boot() {
  adb_for_device wait-for-device
  local deadline=$((SECONDS + 180))
  while [[ "$(adb_for_device shell getprop sys.boot_completed | tr -d '\r')" != "1" ]]; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for $device_serial to boot." >&2
      return 1
    fi
    sleep 1
  done
  # LocaleManager can accept a command before its configuration broadcast is
  # observable by a newly started activity immediately after a reboot.
  sleep 2
}

wait_for_app_locale() {
  local expected_locale="$1"
  local deadline=$((SECONDS + 30))
  local actual_locales=""

  while (( SECONDS < deadline )); do
    actual_locales="$(
      adb_for_device shell cmd locale get-app-locales "$app_package" 2>/dev/null |
        tr -d '\r'
    )"
    if [[ "$actual_locales" == *"[$expected_locale]"* ]]; then
      adb_for_device shell am force-stop "$app_package"
      sleep 1
      return 0
    fi
    sleep 1
  done

  echo "App locale did not settle to $expected_locale: $actual_locales" >&2
  return 1
}

restore_device() {
  (( restore_needed == 1 )) || return 0
  restore_needed=0
  local restore_status=0
  wait_for_boot || restore_status=1

  apply_original_device_settings || restore_status=1
  adb_for_device reboot || restore_status=1
  wait_for_boot || restore_status=1
  # Some emulator images rewrite font and developer settings during boot.
  apply_original_device_settings || restore_status=1
  return "$restore_status"
}

cleanup() {
  local exit_status=$?
  trap - EXIT
  # Ignore a second interrupt until restoration and package cleanup finish.
  trap '' INT TERM

  restore_device || {
    echo "Failed to restore $device_serial; restore its display settings manually." >&2
    exit_status=1
  }
  adb_for_device uninstall "$test_package" >/dev/null 2>&1 || true
  adb_for_device uninstall "$app_package" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
  exit "$exit_status"
}

configure_case() {
  local matrix_case="$1"
  local font_scale
  local force_rtl
  local force_rtl_property

  case "$matrix_case" in
    compact-ltr)
      adb_for_device shell wm size 1080x2400
      adb_for_device shell wm density 420
      font_scale=1.0
      force_rtl=0
      force_rtl_property=false
      ;;
    compact-rtl-large)
      adb_for_device shell wm size 1080x2400
      adb_for_device shell wm density 420
      font_scale=2.0
      force_rtl=1
      force_rtl_property=true
      ;;
    wide-ltr)
      adb_for_device shell wm size 2160x1200
      adb_for_device shell wm density 320
      font_scale=1.0
      force_rtl=0
      force_rtl_property=false
      ;;
    *)
      echo "Unknown matrix case: $matrix_case" >&2
      return 2
      ;;
  esac

  adb_for_device shell settings put system accelerometer_rotation 0
  adb_for_device shell settings put system user_rotation 0
  adb_for_device shell settings put secure show_ime_with_hard_keyboard 1
  adb_for_device reboot
  wait_for_boot
  # Apply these after boot because some API 36 emulator images reset them
  # while restarting. The app is launched only after this configuration.
  adb_for_device shell settings put system font_scale "$font_scale"
  adb_for_device shell settings put global debug.force_rtl "$force_rtl"
  adb_for_device shell setprop debug.force_rtl "$force_rtl_property"
}

run_case() {
  local matrix_case="$1"
  local test_selector="$2"
  local locale_tag
  local result_file="$work_dir/$matrix_case.txt"

  echo "Running smartphone provider UI case: $matrix_case"
  configure_case "$matrix_case"
  adb_for_device shell pm clear "$app_package" >/dev/null
  adb_for_device shell pm clear "$test_package" >/dev/null
  if [[ "$matrix_case" == "compact-rtl-large" ]]; then
    locale_tag="ar-XB"
  else
    locale_tag="en"
  fi
  adb_for_device shell cmd locale set-app-locales "$app_package" --locales "$locale_tag"
  wait_for_app_locale "$locale_tag"

  adb_for_device shell am instrument -w -r \
    -e accessibilityMatrixCase "$matrix_case" \
    -e class "$test_selector" \
    "$runner" \
    | tr -d '\r' \
    | tee "$result_file"

  if ! grep -Eq '^OK \([0-9]+ tests?\)$' "$result_file"; then
    echo "Instrumentation did not report success for $matrix_case." >&2
    return 1
  fi
}

command -v "$adb_command" >/dev/null 2>&1 || {
  echo "ADB command was not found: $adb_command" >&2
  exit 2
}

adb_for_device get-state >/dev/null
if [[ "$(adb_for_device shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "This matrix clears app data and changes display settings; use a disposable emulator." >&2
  exit 2
fi
sdk_level="$(adb_for_device shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ ! "$sdk_level" =~ ^[0-9]+$ ]] || (( sdk_level < 33 )); then
  echo "The per-app locale matrix requires an API 33 or newer emulator." >&2
  exit 2
fi
for package_name in "$app_package" "$test_package"; do
  if adb_for_device shell pm path "$package_name" | tr -d '\r' | grep -q '^package:'; then
    echo "Remove $package_name or use a clean disposable emulator." >&2
    exit 2
  fi
done

original_size_output="$(adb_for_device shell wm size | tr -d '\r')"
original_density_output="$(adb_for_device shell wm density | tr -d '\r')"
original_override_size="$(sed -n 's/^Override size: //p' <<< "$original_size_output")"
original_override_density="$(sed -n 's/^Override density: //p' <<< "$original_density_output")"
original_font_scale="$(read_setting system font_scale)"
original_force_rtl="$(read_setting global debug.force_rtl)"
original_force_rtl_property="$(
  adb_for_device shell getprop debug.force_rtl | tr -d '\r'
)"
original_force_rtl_property="${original_force_rtl_property:-false}"
original_accelerometer_rotation="$(read_setting system accelerometer_rotation)"
original_user_rotation="$(read_setting system user_rotation)"
original_show_ime="$(read_setting secure show_ime_with_hard_keyboard)"
work_dir="$(mktemp -d)"
restore_needed=1
trap cleanup EXIT INT TERM

./gradlew \
  :app:smartphone:assembleDebug \
  :app:smartphone:assembleDebugAndroidTest
adb_for_device install -r "$main_apk"
adb_for_device install -r "$test_apk"

run_case compact-ltr "$full_test"
run_case compact-rtl-large "$matrix_test"
run_case wide-ltr "$matrix_test"
