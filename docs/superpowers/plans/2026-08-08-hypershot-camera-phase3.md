# HyperShot Camera Phase 3 — Cinematic Scene Controls Plan

**Goal:** Add the approved singleplayer Cinematic mode with reversible scene preparation: camera/FOV lock, authoritative WorldClock time lock, weather lock, client-observable chunk readiness, conservative Clean Frame controls, and integrated-server world freeze only through Minecraft's own tick-rate manager when supported.

**Architecture:** Add one `CinematicSceneController` that owns a single restoration snapshot and exposes capabilities before the UI enables controls. `ShotCoordinator` treats Cinematic as another preparation pipeline and still feeds exactly one image to `CaptureManager`. Client-only camera/FOV/visibility locks are isolated mixins/guards; integrated-server state changes happen on the server thread and are restored idempotently on success, cancel, failure, world unload, and client shutdown.

## Task 1 — Pure cinematic state/restoration model
- Add `CinematicOptions`, `CinematicCapabilities`, `ChunkReadiness`, `SceneRestoreState` pure records/enums.
- TDD validation, capability-disabled options, readiness priority, idempotent restoration state transitions.

## Task 2 — Config schema 6
- Preserve schema 5 camera/sequence settings.
- Add defaults: camera lock on, FOV lock on, chunk wait on, chunk timeout 10s, Clean Frame on, time lock current, weather lock current, world freeze off by default.
- Add explicit photographic time/weather choices without silently changing a world.

## Task 3 — Camera/FOV lock
- Add a client `CameraLockController` snapshotting player/camera yaw/pitch and current FOV basis.
- Suppress camera-turn deltas while locked using the narrow existing mouse path; always retain Escape/F8 unlock/cancel.
- Lock FOV through a capture/viewfinder-scoped renderer hook rather than changing user options permanently.
- Restore on every exit/error path.

## Task 4 — Singleplayer scene controller
- Snapshot WorldClock total ticks, weather state, and tick-rate frozen state on integrated-server thread.
- Apply requested time and weather temporarily.
- If world freeze is enabled and Minecraft 26.2 `ServerTickRateManager` supports it, call the vanilla freeze API and restore the previous frozen state; otherwise expose world freeze as unavailable.
- Never mutate permanent game rules just to hold the scene.

## Task 5 — Chunk readiness
- Use client-observable chunk/renderer readiness only; do not claim unseen server terrain is loaded.
- Wait up to configurable timeout, reporting `WAITING_FOR_CHUNKS` with progress/timeout state.
- Timeout choices: capture anyway / keep waiting / cancel in drawer; safe default is remain queued and expose actions.

## Task 6 — Clean Frame
- Reuse existing HUD/hand/block-outline capture flags.
- Add client-only name-tag and supported-particle suppression only if narrow safe renderer hooks exist.
- Guides/viewfinder remain output-excluded independently.
- Do not remove entities/world data.

## Task 7 — ShotCoordinator Cinematic pipeline
- `queueCinematic` preflights first, snapshots scene, applies requested scene options, waits for chunks, runs shader settle, captures one frame, then restores.
- Restoration is attempted exactly once on success and idempotently on every abort/failure path.
- `PREPARING_SCENE`, `WAITING_FOR_CHUNKS`, `SETTLING_SHADERS`, `READY/CAPTURING` remain visible to viewfinder.

## Task 8 — Cinematic drawer/settings UI
- Enable Cinematic only in integrated singleplayer.
- Controls: world freeze (if capability), time lock/preset, weather lock/preset, chunk wait/timeout, camera lock, FOV lock, Clean Frame.
- Show a compact `What will change?` summary before shutter.
- Multiplayer keeps controls visible but disabled with concise ownership explanations.

## Task 9 — Failure restoration guards
- Add source/tests proving camera/FOV locks clear and scene controller restore is called for completion, cancel, failure, world unload, and shutdown.
- Log capture failure and restoration failure separately.

## Task 10 — Verification
- Pure core suite; Java 25 source guards; Gradle clean test/build; packaged JAR inspection; Minecraft 26.2 startup.
- Real hardware: Cinematic no-world-change shot; camera lock; time lock; weather lock; cancel during preparation; injected failure; exact restoration.
- World-freeze is not advertised as supported until tested on a real integrated server.
