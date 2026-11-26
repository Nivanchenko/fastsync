#!/usr/bin/env bash
# Localized .properties are read as ISO-8859-1 by Tycho/p2, so raw UTF-8 there turns into mojibake
# in the update site. Keep those files ASCII-only, non-ASCII characters as \uXXXX escapes.
set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

files=()
while IFS= read -r file; do
  files+=("$file")
done < <(find bundles features -name '*.properties' -not -path '*/target/*' -not -path '*/bin/*')

if [[ ${#files[@]} -eq 0 ]]; then
  printf 'check-encoding: no .properties files found under bundles/ and features/\n' >&2
  exit 2
fi

status=0
# In the C locale [[:print:]] is ASCII 0x20-0x7E, so any non-ASCII byte matches.
LC_ALL=C grep -n '[^[:print:][:space:]]' "${files[@]}" || status=$?

case "$status" in
  0)
    printf '\ncheck-encoding: non-ASCII characters found; use \\uXXXX escapes (native2ascii format).\n' >&2
    exit 1
    ;;
  1)
    exit 0
    ;;
  *)
    printf 'check-encoding: grep failed with exit code %d\n' "$status" >&2
    exit "$status"
    ;;
esac
