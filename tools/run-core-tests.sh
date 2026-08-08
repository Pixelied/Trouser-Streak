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
java -cp "$OUT" dev.hypershot.core.CoreTestMain
java -cp "$OUT" dev.hypershot.core.CameraCoreTestMain
