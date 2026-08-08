# Testing

## Core regression suite

Run `./tools/run-core-tests.sh`. It compiles only dependency-free production core classes and executes `CoreTestMain` with Java assertions represented as explicit checks. Synthetic images are small; the tests exercise the same streaming and disk-backed paths without generating multi-gigabyte files.

## Full build

Run `./gradlew clean build --stacktrace`. `check` depends on `coreTest`.

## Manual game smoke matrix

1. OpenGL: native capture, hidden HUD, 4K scaled, 10K tiled, cancel during tiles, gallery thumbnail, preview/reveal.
2. Vulkan: repeat the same matrix and inspect for backend validation errors.
3. Non-divisible size such as 10001×7777 with overlap.
4. Force an excessive initial tile to verify automatic reduction.
5. Cancel and intentionally fail output permissions; confirm HUD/hand/projection/target restore.
6. Shader pack: verify native mode and test tiled overlap; do not mark the pack compatible if history effects diverge.
