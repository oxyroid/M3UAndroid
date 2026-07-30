#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
runner="$repo_root/testing/bin/run-smartphone-provider-ui-matrix.sh"
matrix_description="$("$runner" --describe)"

bash -n "$runner"

case_count="$(awk -F '\t' 'NR > 1 { count += 1 } END { print count + 0 }' \
  <<< "$matrix_description")"
if [[ "$case_count" -ne 8 ]]; then
  echo "Expected 8 bounded UI matrix cases, found $case_count." >&2
  exit 1
fi

for width in 360 599 600 839 840; do
  if ! awk -F '\t' -v expected="$width" \
      'NR > 1 && $3 == expected { found = 1 } END { exit !found }' \
      <<< "$matrix_description"; then
    echo "Missing required width boundary: ${width}dp." >&2
    exit 1
  fi
done

for locale in en zh-CN en-XA ar-XB; do
  if ! awk -F '\t' -v expected="$locale" \
      'NR > 1 && $6 == expected { found = 1 } END { exit !found }' \
      <<< "$matrix_description"; then
    echo "Missing required locale: $locale." >&2
    exit 1
  fi
done

for theme in light dark; do
  if ! awk -F '\t' -v expected="$theme" \
      'NR > 1 && $7 == expected { found = 1 } END { exit !found }' \
      <<< "$matrix_description"; then
    echo "Missing required theme: $theme." >&2
    exit 1
  fi
done

if ! awk -F '\t' \
    'NR > 1 && $4 == 480 { found = 1 } END { exit !found }' \
    <<< "$matrix_description"; then
  echo "Missing the constrained 480dp-height case." >&2
  exit 1
fi
if ! awk -F '\t' \
    'NR > 1 && $8 == "threebutton" { found = 1 } END { exit !found }' \
    <<< "$matrix_description"; then
  echo "Missing three-button navigation coverage." >&2
  exit 1
fi
if ! awk -F '\t' \
    'NR > 1 && $5 == "2.0" && $6 == "ar-XB" { found = 1 } END { exit !found }' \
    <<< "$matrix_description"; then
  echo "Missing the required RTL + 200% text stress case." >&2
  exit 1
fi

if ! awk -F '\t' '
    NR > 1 && $1 == "phone" && $3 >= 600 { exit 1 }
    NR > 1 && $1 == "tablet" && $3 < 600 { exit 1 }
  ' <<< "$matrix_description"; then
  echo "Phone and tablet width cases are mixed between device profiles." >&2
  exit 1
fi

printf '%s\n' "Smartphone provider UI matrix contract verified."
