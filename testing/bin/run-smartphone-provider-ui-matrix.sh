#!/usr/bin/env bash

set -euo pipefail

device_serial="${1:-}"
device_profile="${2:-}"
adb_command="${ADB_COMMAND:-adb}"
test_class="com.m3u.testing.SubscriptionSourceSelectionTest"
content_padding_test="com.m3u.testing.SubscriptionContentPaddingTest"
extension_test="com.m3u.testing.ExternalExtensionManagementUiTest"
floating_navigation_test="com.m3u.testing.FloatingNavigationBehaviorTest"
floating_remote_dock_test="com.m3u.testing.FloatingRemoteControlDockTest"
extension_choice_layout_test="com.m3u.smartphone.ui.business.setting.fragments.ExtensionSettingChoiceLayoutTest"
playlist_adaptive_test="com.m3u.testing.PlaylistAdaptiveLayoutTest"
remote_control_accessibility_test="com.m3u.smartphone.ui.common.connect.RemoteControlAccessibilityTest"
playlist_flow_test="com.m3u.testing.PlaylistManagementFlowTest"
debug_default_library_test="com.m3u.testing.DebugDefaultLibraryBootstrapTest"
playlist_flow_phone_tests="$playlist_flow_test#existingPlaylistRowOpensItsConfigurationScreen,$playlist_flow_test#blankConfigurationTitleCannotBeSaved,$playlist_flow_test#emptyM3uSubmissionShowsErrorsAndStaysOnEditor,$playlist_flow_test#reopeningM3uEditorStartsWithAFreshDraft,$playlist_flow_test#acceptedM3uSubmissionReturnsToPlaylistManagementOverview,$playlist_flow_test#removingPlaylistRequiresConfirmationAndReturnsToManagement"
playlist_flow_tablet_tests="$playlist_flow_test#wideTabletSettingsListReturnsPlaylistEditorToManagementRoot,$playlist_flow_test#wideTabletKeepsPlaylistConfigurationInsideSettingsContext"
full_test="$debug_default_library_test,$test_class,$content_padding_test,$extension_test,$floating_navigation_test,$floating_remote_dock_test,$playlist_flow_phone_tests"
matrix_test="$test_class#providerFormWorksInRequestedAccessibilityConfiguration,$content_padding_test#overviewRestoreActionCanScrollAboveTheSystemSafeArea,$extension_test"
rtl_large_test="$matrix_test,$extension_choice_layout_test,$floating_navigation_test#compactNavigationStaysForScrollActionAndHidesForDetailAndSearch,$floating_remote_dock_test,$playlist_adaptive_test#epgLeafDeleteActionDoesNotOverlapContentAtTwoHundredPercentText,$remote_control_accessibility_test"
narrow_test="$test_class#providerFormWorksInRequestedAccessibilityConfiguration,$playlist_adaptive_test#narrowWidthOverviewSourcePickerAndEditorActionsRemainUsable"
medium_test="$playlist_adaptive_test#mediumWidthSideRailUsesSinglePlaylistPaneHeadersAndBackNavigation"
wide_test="$matrix_test,$playlist_flow_tablet_tests"
runner="com.m3u.smartphone.test/androidx.test.runner.AndroidJUnitRunner"
app_package="com.m3u.smartphone"
test_package="com.m3u.smartphone.test"
reference_extension_package="com.m3u.testing.extension.reference"
main_apk="app/smartphone/build/outputs/apk/debug/smartphone-debug.apk"
test_apk="app/smartphone/build/outputs/apk/androidTest/debug/smartphone-debug-androidTest.apk"
reference_extension_apk="testing/extension-reference/build/outputs/apk/debug/extension-reference-debug.apk"
work_dir=""
restore_needed=0
original_enabled_navigation_overlays=""
navigation_overlay_packages=(
  "com.android.internal.systemui.navbar.gestural"
  "com.android.internal.systemui.navbar.threebutton"
  "com.android.internal.systemui.navbar.twobutton"
)
phone_cases=(
  "compact-ltr"
  "compact-narrow-ltr"
  "compact-height-zh-cn-dark-three-button"
  "compact-599-en-xa"
  "compact-rtl-large"
)
tablet_cases=(
  "medium-600-ltr"
  "medium-839-ltr"
  "expanded-840-ltr-dark"
)

load_case_spec() {
  local matrix_case="$1"

  case "$matrix_case" in
    compact-ltr)
      case_width_dp=360
      case_height_dp=800
      case_density=320
      case_font_scale=1.0
      case_locale="en"
      case_night_mode=1
      case_theme="light"
      case_navigation="gestural"
      case_instrumentation_case="compact-ltr"
      case_test_selector="$full_test"
      ;;
    compact-height-zh-cn-dark-three-button)
      case_width_dp=360
      case_height_dp=480
      case_density=320
      case_font_scale=1.0
      case_locale="zh-CN"
      case_night_mode=2
      case_theme="dark"
      case_navigation="threebutton"
      case_instrumentation_case="$matrix_case"
      case_test_selector="$test_class#jellyfinPasswordFieldIsBroughtAboveTheIme"
      case_test_selector+=",$content_padding_test#overviewRestoreActionCanScrollAboveTheSystemSafeArea"
      ;;
    compact-narrow-ltr)
      case_width_dp=320
      case_height_dp=720
      case_density=400
      case_font_scale=1.0
      case_locale="en"
      case_night_mode=1
      case_theme="light"
      case_navigation="gestural"
      case_instrumentation_case="compact-narrow-ltr"
      case_test_selector="$narrow_test"
      ;;
    compact-599-en-xa)
      case_width_dp=599
      case_height_dp=800
      case_density=320
      case_font_scale=1.0
      case_locale="en-XA"
      case_night_mode=1
      case_theme="light"
      case_navigation="gestural"
      case_instrumentation_case="$matrix_case"
      case_test_selector="$test_class#sourceRowsExposeLocalizedNamesAndButtonRolesAndCanNavigateBack"
      case_test_selector+=",$extension_test#directDetailRenderingDistinguishesLookupStatesAndBusyPluginContent"
      case_test_selector+=",$floating_navigation_test#compactNavigationStaysForScrollActionAndHidesForDetailAndSearch"
      ;;
    compact-rtl-large)
      case_width_dp=320
      case_height_dp=720
      case_density=400
      case_font_scale=2.0
      case_locale="ar-XB"
      case_night_mode=2
      case_theme="dark"
      case_navigation="gestural"
      case_instrumentation_case="compact-rtl-large"
      case_test_selector="$rtl_large_test"
      ;;
    medium-600-ltr)
      case_width_dp=600
      case_height_dp=900
      case_density=320
      case_font_scale=1.0
      case_locale="en"
      case_night_mode=1
      case_theme="light"
      case_navigation="gestural"
      case_instrumentation_case="medium-ltr"
      case_test_selector="$medium_test"
      ;;
    medium-839-ltr)
      case_width_dp=839
      case_height_dp=600
      case_density=320
      case_font_scale=1.0
      case_locale="en"
      case_night_mode=1
      case_theme="light"
      case_navigation="gestural"
      case_instrumentation_case="medium-ltr"
      case_test_selector="$medium_test"
      ;;
    expanded-840-ltr-dark)
      case_width_dp=840
      case_height_dp=600
      case_density=320
      case_font_scale=1.0
      case_locale="en"
      case_night_mode=2
      case_theme="dark"
      case_navigation="gestural"
      case_instrumentation_case="wide-ltr"
      case_test_selector="$wide_test"
      ;;
    *)
      echo "Unknown matrix case: $matrix_case" >&2
      return 2
      ;;
  esac

  if (( case_width_dp * case_density % 160 != 0 )) ||
      (( case_height_dp * case_density % 160 != 0 )); then
    echo "Case does not map to whole physical pixels: $matrix_case" >&2
    return 2
  fi
  case_width_px=$((case_width_dp * case_density / 160))
  case_height_px=$((case_height_dp * case_density / 160))
}

describe_case() {
  local profile="$1"
  local matrix_case="$2"

  load_case_spec "$matrix_case"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$profile" \
    "$matrix_case" \
    "$case_width_dp" \
    "$case_height_dp" \
    "$case_font_scale" \
    "$case_locale" \
    "$case_theme" \
    "$case_navigation" \
    "$case_instrumentation_case"
}

describe_matrix() {
  local matrix_case

  printf 'profile\tcase\twidth_dp\theight_dp\tfont_scale\tlocale\ttheme\tnavigation\tinstrumentation_case\n'
  for matrix_case in "${phone_cases[@]}"; do
    describe_case "phone" "$matrix_case"
  done
  for matrix_case in "${tablet_cases[@]}"; do
    describe_case "tablet" "$matrix_case"
  done
}

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

enabled_navigation_overlays() {
  adb_for_device shell cmd overlay list --user 0 |
    tr -d '\r' |
    sed -n \
      's/^[[:space:]]*\[x\][[:space:]]*\(com\.android\.internal\.systemui\.navbar\.[[:alnum:]_-]*\)$/\1/p'
}

navigation_overlay_package() {
  case "$1" in
    gestural)
      printf '%s\n' "com.android.internal.systemui.navbar.gestural"
      ;;
    threebutton)
      printf '%s\n' "com.android.internal.systemui.navbar.threebutton"
      ;;
    *)
      echo "Unsupported navigation mode: $1" >&2
      return 2
      ;;
  esac
}

navigation_overlay_is_available() {
  local overlay_package="$1"

  adb_for_device shell cmd overlay list --user 0 |
    tr -d '\r' |
    sed -n 's/^[[:space:]]*\[[ x-]\][[:space:]]*//p' |
    grep -Fxq "$overlay_package"
}

set_navigation_mode() {
  local navigation_mode="$1"
  local overlay_package

  overlay_package="$(navigation_overlay_package "$navigation_mode")"
  if ! navigation_overlay_is_available "$overlay_package"; then
    echo "Navigation overlay is unavailable: $overlay_package" >&2
    return 1
  fi
  adb_for_device shell cmd overlay enable-exclusive \
    --category \
    --user 0 \
    "$overlay_package" >/dev/null
}

restore_navigation_overlays() {
  local overlay_package
  local restore_status=0

  for overlay_package in "${navigation_overlay_packages[@]}"; do
    if navigation_overlay_is_available "$overlay_package"; then
      adb_for_device shell cmd overlay disable \
        --user 0 \
        "$overlay_package" >/dev/null || restore_status=1
    fi
  done
  while IFS= read -r overlay_package; do
    [[ -n "$overlay_package" ]] || continue
    adb_for_device shell cmd overlay enable \
      --user 0 \
      "$overlay_package" >/dev/null || restore_status=1
  done <<< "$original_enabled_navigation_overlays"

  return "$restore_status"
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
  restore_setting secure ui_night_mode \
    "$original_ui_night_mode" || apply_status=1
  restore_navigation_overlays || apply_status=1
  restore_setting secure navigation_mode \
    "$original_navigation_mode" || apply_status=1
  if [[ -n "$original_force_rtl_property" ]]; then
    adb_for_device shell setprop debug.force_rtl \
      "$original_force_rtl_property" || apply_status=1
  else
    # adb's argument protocol drops an empty final argument. Let the device
    # shell parse an explicitly quoted empty value instead.
    adb_for_device shell "setprop debug.force_rtl ''" || apply_status=1
  fi

  return "$apply_status"
}

wait_for_boot() {
  local deadline=$((SECONDS + 180))
  while ! adb_for_device get-state >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for $device_serial to connect." >&2
      return 1
    fi
    sleep 1
  done
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

uninstall_test_package() {
  local package_name="$1"

  if ! adb_for_device shell pm path "$package_name" 2>/dev/null |
      tr -d '\r' |
      grep -q '^package:'; then
    return 0
  fi
  adb_for_device uninstall "$package_name" >/dev/null
  if adb_for_device shell pm path "$package_name" 2>/dev/null |
      tr -d '\r' |
      grep -q '^package:'; then
    echo "Package remains installed after cleanup: $package_name" >&2
    return 1
  fi
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
  # A second signal is an explicit request to stop even if the device is lost.
  trap 'exit 130' INT TERM

  restore_device || {
    echo "Failed to restore $device_serial; restore its display settings manually." >&2
    exit_status=1
  }
  uninstall_test_package "$test_package" || exit_status=1
  uninstall_test_package "$app_package" || exit_status=1
  uninstall_test_package "$reference_extension_package" || exit_status=1
  if [[ -n "$work_dir" && -d "$work_dir" ]]; then
    rm -rf -- "$work_dir"
  fi
  exit "$exit_status"
}

apply_case_device_settings() {
  adb_for_device shell wm size "${case_width_px}x${case_height_px}"
  adb_for_device shell wm density "$case_density"
  adb_for_device shell settings put system accelerometer_rotation 0
  adb_for_device shell settings put system user_rotation 0
  adb_for_device shell settings put secure show_ime_with_hard_keyboard 1
  adb_for_device shell settings put system font_scale "$case_font_scale"
  adb_for_device shell settings put global debug.force_rtl 0
  adb_for_device shell settings put secure ui_night_mode "$case_night_mode"
  adb_for_device shell setprop debug.force_rtl false
  set_navigation_mode "$case_navigation"
}

assert_case_device_settings() {
  local matrix_case="$1"
  local expected_size="${case_width_px}x${case_height_px}"
  local actual_size
  local actual_density
  local actual_font_scale
  local actual_night_mode
  local expected_navigation_overlay

  actual_size="$(
    adb_for_device shell wm size |
      tr -d '\r' |
      sed -n 's/^Override size: //p'
  )"
  actual_density="$(
    adb_for_device shell wm density |
      tr -d '\r' |
      sed -n 's/^Override density: //p'
  )"
  actual_font_scale="$(read_setting system font_scale)"
  actual_night_mode="$(read_setting secure ui_night_mode)"
  expected_navigation_overlay="$(
    navigation_overlay_package "$case_navigation"
  )"

  [[ "$actual_size" == "$expected_size" ]] || {
    echo "$matrix_case size is $actual_size; expected $expected_size." >&2
    return 1
  }
  [[ "$actual_density" == "$case_density" ]] || {
    echo "$matrix_case density is $actual_density; expected $case_density." >&2
    return 1
  }
  [[ "$actual_font_scale" == "$case_font_scale" ]] || {
    echo "$matrix_case font scale is $actual_font_scale; expected $case_font_scale." >&2
    return 1
  }
  [[ "$actual_night_mode" == "$case_night_mode" ]] || {
    echo "$matrix_case night mode is $actual_night_mode; expected $case_night_mode." >&2
    return 1
  }
  if ! enabled_navigation_overlays |
      grep -Fxq "$expected_navigation_overlay"; then
    echo "$matrix_case navigation overlay is not active: $expected_navigation_overlay" >&2
    return 1
  fi
}

configure_case() {
  local matrix_case="$1"

  load_case_spec "$matrix_case"
  apply_case_device_settings
  adb_for_device reboot
  wait_for_boot
  # API 36 emulator images may rewrite display, font, theme, or developer
  # settings during boot. Reapply and verify before the app is launched.
  apply_case_device_settings
  assert_case_device_settings "$matrix_case"
}

run_case() {
  local matrix_case="$1"
  local result_file="$work_dir/$matrix_case.txt"

  configure_case "$matrix_case"
  echo "Running smartphone provider UI case: $matrix_case " \
    "(${case_width_dp}x${case_height_dp}dp, ${case_locale}, " \
    "${case_theme}, ${case_navigation}, ${case_font_scale}x text)"
  adb_for_device shell pm clear "$app_package" >/dev/null
  adb_for_device shell pm clear "$test_package" >/dev/null
  adb_for_device shell cmd locale set-app-locales \
    "$app_package" \
    --locales "$case_locale"
  wait_for_app_locale "$case_locale"

  adb_for_device shell am instrument -w -r \
    -e accessibilityMatrixCase "$case_instrumentation_case" \
    -e class "$case_test_selector" \
    "$runner" \
    | tr -d '\r' \
    | tee "$result_file"

  if ! grep -Eq '^OK \([0-9]+ tests?\)$' "$result_file"; then
    echo "Instrumentation did not report success for $matrix_case." >&2
    return 1
  fi
}

if [[ "$#" -eq 1 && "$1" == "--describe" ]]; then
  describe_matrix
  exit 0
fi
if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <emulator-serial> <phone|tablet>" >&2
  echo "       $0 --describe" >&2
  exit 2
fi
case "$device_profile" in
  phone|tablet)
    ;;
  *)
    echo "Unknown device profile: $device_profile" >&2
    exit 2
    ;;
esac

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
for package_name in "$app_package" "$test_package" "$reference_extension_package"; do
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
original_accelerometer_rotation="$(read_setting system accelerometer_rotation)"
original_user_rotation="$(read_setting system user_rotation)"
original_show_ime="$(read_setting secure show_ime_with_hard_keyboard)"
original_ui_night_mode="$(read_setting secure ui_night_mode)"
original_navigation_mode="$(read_setting secure navigation_mode)"
original_enabled_navigation_overlays="$(enabled_navigation_overlays)"
work_dir="$(mktemp -d)"
restore_needed=1
trap cleanup EXIT INT TERM

./gradlew --no-daemon --max-workers=1 \
  :app:smartphone:assembleDebug \
  :app:smartphone:assembleDebugAndroidTest \
  :testing:extension-reference:assembleDebug
adb_for_device install -r "$main_apk"
adb_for_device install -r "$test_apk"
adb_for_device install -r "$reference_extension_apk"

case "$device_profile" in
  phone)
    for matrix_case in "${phone_cases[@]}"; do
      run_case "$matrix_case"
    done
    ;;
  tablet)
    # Keep every >=600dp case on the dedicated tablet AVD.
    for matrix_case in "${tablet_cases[@]}"; do
      run_case "$matrix_case"
    done
    ;;
esac
