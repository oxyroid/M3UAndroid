#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "Usage: $0 <apk-or-native-pack.zip> [...]" >&2
  exit 2
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

readelf_command=""
if command -v llvm-readelf >/dev/null 2>&1; then
  readelf_command="$(command -v llvm-readelf)"
elif command -v readelf >/dev/null 2>&1; then
  readelf_command="$(command -v readelf)"
fi

objdump_command=""
if [[ -z "$readelf_command" ]] && command -v objdump >/dev/null 2>&1; then
  objdump_command="$(command -v objdump)"
fi

if [[ -z "$readelf_command" && -z "$objdump_command" ]]; then
  echo "Neither readelf nor objdump is available." >&2
  exit 2
fi

zipalign_command=""
android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$android_sdk_root" && -f local.properties ]]; then
  android_sdk_root="$(
    sed -n 's/^sdk[.]dir=//p' local.properties \
      | head -n 1 \
      | sed 's/\\:/:/g; s/\\\\/\\/g'
  )"
fi
if [[ -z "$android_sdk_root" && -d "$HOME/Library/Android/sdk" ]]; then
  android_sdk_root="$HOME/Library/Android/sdk"
fi
if [[ -n "$android_sdk_root" && -d "$android_sdk_root/build-tools" ]]; then
  zipalign_command="$(
    find "$android_sdk_root/build-tools" -type f -name zipalign -perm -u+x \
      | sort \
      | tail -n 1
  )"
fi

failure_count=0
library_count=0
archive_index=0

relro_is_file_suffix() {
  local records="$1"
  local relro_offset
  local relro_size
  local found=0

  relro_offset="$(awk '$1 == "RELRO" { print $2; exit }' <<< "$records")"
  relro_size="$(awk '$1 == "RELRO" { print $3; exit }' <<< "$records")"
  [[ -n "$relro_offset" && -n "$relro_size" ]] || return 1

  while read -r type load_offset load_size; do
    [[ "$type" == "LOAD" ]] || continue
    if ((
      relro_offset >= load_offset &&
      relro_offset + relro_size == load_offset + load_size
    )); then
      found=1
      break
    fi
  done <<< "$records"

  (( found == 1 ))
}

for archive in "$@"; do
  if [[ ! -f "$archive" ]]; then
    echo "Missing archive: $archive" >&2
    exit 2
  fi

  archive_root="$work_dir/$archive_index"
  archive_index=$((archive_index + 1))
  mkdir -p "$archive_root"

  while IFS= read -r entry; do
    [[ -n "$entry" ]] || continue
    output="$archive_root/$entry"
    mkdir -p "$(dirname "$output")"
    unzip -p "$archive" "$entry" > "$output"
  done < <(
    unzip -Z1 "$archive" \
      | awk '
          $0 ~ "^lib/[^/]+/[^/]+[.]so$" ||
          $0 ~ "^jni/[^/]+/[^/]+[.]so$" ||
          $0 ~ "^lib[^/]+[.]so$"
        '
  )

  while IFS= read -r -d '' library; do
    library_count=$((library_count + 1))
    relative_library="${library#"$archive_root"/}"

    if [[ -n "$readelf_command" ]]; then
      program_headers="$("$readelf_command" -lW "$library")"
      alignments="$(
        awk '$1 == "LOAD" { print $NF }' <<< "$program_headers"
      )"
      if [[ -z "$alignments" ]]; then
        echo "UNALIGNED $archive!/$relative_library (no LOAD segments)" >&2
        failure_count=$((failure_count + 1))
        continue
      fi
      while IFS= read -r alignment; do
        alignment_value="${alignment#0x}"
        if (( 16#$alignment_value < 16#4000 )); then
          echo "UNALIGNED $archive!/$relative_library (LOAD $alignment)" >&2
          failure_count=$((failure_count + 1))
        fi
      done <<< "$alignments"
      if ! grep -q 'GNU_RELRO' <<< "$program_headers"; then
        echo "NO_RELRO $archive!/$relative_library" >&2
        failure_count=$((failure_count + 1))
      elif ! relro_is_file_suffix "$(
        awk '
          $1 == "LOAD" { print "LOAD", $2, $5 }
          $1 == "GNU_RELRO" { print "RELRO", $2, $5 }
        ' <<< "$program_headers"
      )"; then
        echo "UNSAFE_RELRO $archive!/$relative_library" >&2
        failure_count=$((failure_count + 1))
      fi
    else
      program_headers="$("$objdump_command" -p "$library")"
      alignments="$(
        awk '/^[[:space:]]*LOAD off/ { print $NF }' <<< "$program_headers"
      )"
      if [[ -z "$alignments" ]]; then
        echo "UNALIGNED $archive!/$relative_library (no LOAD segments)" >&2
        failure_count=$((failure_count + 1))
        continue
      fi
      while IFS= read -r alignment; do
        if [[ ! "$alignment" =~ ^2\*\*[0-9]+$ ]]; then
          echo "UNALIGNED $archive!/$relative_library (unknown LOAD $alignment)" >&2
          failure_count=$((failure_count + 1))
          continue
        fi
        exponent="${alignment:3}"
        if (( exponent < 14 )); then
          echo "UNALIGNED $archive!/$relative_library (LOAD $alignment)" >&2
          failure_count=$((failure_count + 1))
        fi
      done <<< "$alignments"
      if ! grep -q 'RELRO' <<< "$program_headers"; then
        echo "NO_RELRO $archive!/$relative_library" >&2
        failure_count=$((failure_count + 1))
      elif ! relro_is_file_suffix "$(
        awk '
          /^[[:space:]]*LOAD off/ {
            type = "LOAD"
            offset = $3
            next
          }
          /^[[:space:]]*RELRO off/ {
            type = "RELRO"
            offset = $3
            next
          }
          /^[[:space:]]*filesz/ && type != "" {
            print type, offset, $2
            type = ""
          }
        ' <<< "$program_headers"
      )"; then
        echo "UNSAFE_RELRO $archive!/$relative_library" >&2
        failure_count=$((failure_count + 1))
      fi
    fi
  done < <(find "$archive_root" -type f -name '*.so' -print0)

  if [[ "$archive" == *.apk ]]; then
    if [[ -z "$zipalign_command" ]]; then
      echo "zipalign was not found under the Android SDK." >&2
      exit 2
    fi
    if ! "$zipalign_command" -c -P 16 -v 4 "$archive" >/dev/null; then
      echo "ZIP_UNALIGNED $archive" >&2
      failure_count=$((failure_count + 1))
    fi
  fi
done

if (( library_count == 0 )); then
  echo "No native libraries found in the supplied archives." >&2
  exit 2
fi

if (( failure_count != 0 )); then
  echo "16 KB verification failed with $failure_count finding(s)." >&2
  exit 1
fi

echo "16 KB verification passed for $library_count native libraries."
