#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/standalone-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
{
  find "$ROOT/src/main/java/dev/hypershot/core" -name '*.java' -print0
  find "$ROOT/src/test/java/dev/hypershot/core" -name '*TestMain.java' -print0
} | xargs -0 javac --release 21 -d "$OUT"
for test_source in "$ROOT"/src/test/java/dev/hypershot/core/*TestMain.java; do
  test_class="$(basename "$test_source" .java)"
  java -cp "$OUT" "dev.hypershot.core.$test_class"
done
