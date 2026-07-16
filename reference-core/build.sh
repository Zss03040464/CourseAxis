#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build"
mkdir -p "$OUT"
kotlinc "$ROOT"/src/courseaxis/*.kt "$ROOT/test/TestMain.kt" -include-runtime -d "$OUT/reference-tests.jar"
java -jar "$OUT/reference-tests.jar"
