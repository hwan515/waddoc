#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

export GRADLE_USER_HOME="${PROJECT_ROOT}/.gradle-local"

cd "${PROJECT_ROOT}"
exec ./gradlew bootRun
