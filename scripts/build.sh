#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
: "${JAVA_HOME:=/Applications/1C/1CE/components/axiom-jdk-full-25.0.2+12-x86_64}"
MAVEN_HOME="$HOME/.local/opt/apache-maven-3.9.16"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  printf 'JDK 25 not found: %s\n' "$JAVA_HOME" >&2
  exit 1
fi

if [[ ! -x "$MAVEN_HOME/bin/mvn" ]]; then
  printf 'Maven 3.9.16 not found: %s\nRun ./scripts/install-maven.sh first.\n' "$MAVEN_HOME" >&2
  exit 1
fi

export JAVA_HOME MAVEN_HOME
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

cd "$REPO_ROOT"
exec "$MAVEN_HOME/bin/mvn" -B -ntp clean verify -Dtycho.localArtifacts=ignore
