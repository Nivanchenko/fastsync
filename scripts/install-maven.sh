#!/usr/bin/env bash
set -euo pipefail

MAVEN_VERSION=3.9.16
INSTALL_ROOT="${MAVEN_INSTALL_ROOT:-$HOME/.local/opt}"
if [[ -z "$INSTALL_ROOT" || "$INSTALL_ROOT" == "/" ]]; then
  printf 'MAVEN_INSTALL_ROOT must be a non-root directory.\n' >&2
  exit 1
fi
INSTALL_ROOT="${INSTALL_ROOT%/}"
MAVEN_HOME="$INSTALL_ROOT/apache-maven-$MAVEN_VERSION"
ARCHIVE="apache-maven-$MAVEN_VERSION-bin.tar.gz"
BASE_URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries"

if [[ -e "$MAVEN_HOME" ]]; then
  if [[ -x "$MAVEN_HOME/bin/mvn" ]]; then
    printf 'Maven %s is already present at %s\n' "$MAVEN_VERSION" "$MAVEN_HOME"
    printf 'Use it in this shell with:\n'
    printf 'export MAVEN_HOME=%q\n' "$MAVEN_HOME"
    printf 'export PATH="$MAVEN_HOME/bin:$PATH"\n'
    exit 0
  fi
  printf 'Refusing to overwrite existing path: %s\n' "$MAVEN_HOME" >&2
  exit 1
fi

for tool in curl shasum tar; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$tool" >&2
    exit 1
  fi
done

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/install-maven.XXXXXX")"
trap 'rm -rf "$TEMP_DIR"' EXIT

curl -fsSL --retry 3 "$BASE_URL/$ARCHIVE" -o "$TEMP_DIR/$ARCHIVE"
curl -fsSL --retry 3 "$BASE_URL/$ARCHIVE.sha512" -o "$TEMP_DIR/$ARCHIVE.sha512"

EXPECTED_SHA512="$(awk 'NR == 1 { print $1 }' "$TEMP_DIR/$ARCHIVE.sha512")"
if [[ ! "$EXPECTED_SHA512" =~ ^[[:xdigit:]]{128}$ ]]; then
  printf 'Could not parse the official SHA-512 checksum.\n' >&2
  exit 1
fi

ACTUAL_SHA512="$(shasum -a 512 "$TEMP_DIR/$ARCHIVE" | awk '{ print $1 }')"
if [[ "$ACTUAL_SHA512" != "$EXPECTED_SHA512" ]]; then
  printf 'SHA-512 verification failed for %s\n' "$ARCHIVE" >&2
  exit 1
fi

tar -xzf "$TEMP_DIR/$ARCHIVE" -C "$TEMP_DIR"
if [[ ! -x "$TEMP_DIR/apache-maven-$MAVEN_VERSION/bin/mvn" ]]; then
  printf 'The archive does not contain the expected Maven executable.\n' >&2
  exit 1
fi

mkdir -p "$INSTALL_ROOT"
mv "$TEMP_DIR/apache-maven-$MAVEN_VERSION" "$MAVEN_HOME"

printf 'Maven %s installed at %s\n' "$MAVEN_VERSION" "$MAVEN_HOME"
printf 'Use it in this shell with:\n'
printf 'export MAVEN_HOME=%q\n' "$MAVEN_HOME"
printf 'export PATH="$MAVEN_HOME/bin:$PATH"\n'
