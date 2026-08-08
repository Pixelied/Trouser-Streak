# HyperShot Camera Phase 2 — Burst + Time Bracketing Plan

**Goal:** Add real Burst and singleplayer Time-of-Day Bracketing on top of the green Phase 1 Photo coordinator, with whole-session preflight, grouped Gallery metadata, and exact restoration of any temporary time-control state.

**Architecture:** Generalize `ShotCoordinator` from one pending Photo into a sequence session while keeping `CaptureManager` strictly one-image-at-a-time. Pure sequence plans calculate frame order/workload. `CaptureGroupContext` tags completed captures for Gallery grouping. Time control is isolated behind a singleplayer-only `TimeSceneController`; multiplayer returns unavailable instead of faking control.

## Task 1 — Pure Burst and sequence workload models
- Add `BurstPlan` with count 1..100 and interval 0..60_000 ms.
- Add `SequenceEstimate`/calculator using checked arithmetic across all frames.
- TDD: 3/5/10 counts, interval behavior, 10×32K pixel count, overflow rejection.

## Task 2 — Pure Time bracket model
- Add named states `SUNRISE`, `MORNING`, `NOON`, `GOLDEN_HOUR`, `SUNSET`, `BLUE_HOUR`, `NIGHT` with explicit Minecraft tick values documented in code.
- Add custom start/end/step/wrap sequence generation.
- TDD order, wrap, invalid step, labels.

## Task 3 — Config schema 5
- Preserve schema 4 camera settings and capture presets.
- Add Burst defaults: count 5, interval 250 ms.
- Add Time defaults: curated seven-state sequence, weather lock on, settle between states on.
- Validate/clamp all values.

## Task 4 — Capture group metadata
- Extend `CaptureRecord` with optional `groupId`, `groupType`, `groupIndex`, `groupCount`, `groupLabel`, `groupComplete` while remaining backward-compatible with schema-1 records.
- Add a `CaptureGroupContext` mapping capture IDs to group metadata before notifications create Gallery records.
- Update search metadata; preserve ordinary Photo records unchanged.

## Task 5 — Generalize ShotCoordinator sequence session
- Add `queueBurst` and `queueTimeBracket`.
- Session owns immutable per-frame requests, interval deadlines, current index, total workload, and group ID.
- Each frame starts only after the previous `CaptureManager` completion callback.
- Mid-sequence failure stops remaining frames, preserves completed files, marks group incomplete.
- Cancel stops pending frame/active capture and sequence.

## Task 6 — Burst mode
- Camera drawer mode strip enables Photo/Burst.
- Burst controls: 3/5/10/custom count and Every available frame/100/250/500/1000/custom ms.
- Whole burst preflight blocks before frame one if `CANNOT_START`.
- High-resolution 'burst' is labeled Capture Sequence when cadence cannot be real-time.

## Task 7 — Singleplayer TimeSceneController
- Capability check requires integrated server and current dimension server level.
- Snapshot original day time and daylight-cycle gamerule value.
- Disable daylight cycle for bracket session, set target time on integrated-server thread, wait until client observes the target, then run Shader Settle and capture.
- Restore exact original day time + gamerule on success/cancel/failure/world exit.
- Restoration idempotent.
- Multiplayer: unavailable with explicit UI explanation.

## Task 8 — Time mode UI
- Enable Time mode only when `TimeSceneController` capability says yes.
- Curated sequence + custom range controls.
- Show frame count, lighting label, total pixel workload, settle state, and restoration status.

## Task 9 — Gallery grouping polish
- Group consecutive Burst/Time frames visually in Gallery with `Burst 4/10` or `Golden Hour` labels.
- Opening a group shows contact-sheet thumbnails using existing bounded thumbnails only.
- Favorite/open/delete remain per-frame; group delete is explicit.
- Never decode all full-resolution files for the stack view.

## Task 10 — Verification
- Pure core suite + Java 25 source guards.
- Gradle clean test/build and packaged JAR inspection.
- Minecraft startup smoke.
- Real hardware: 3-shot Native Burst; 5-shot 4K sequence; singleplayer 3-state Time bracket; exact time/daylight-cycle restoration after success and forced cancel/failure.
- Do not claim 8K/16K multi-frame sequences validated until exercised on real hardware.
