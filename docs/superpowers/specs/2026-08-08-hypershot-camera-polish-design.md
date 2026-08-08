# HyperShot camera-mode and cinematic photography polish design

Date: 2026-08-08
Target: HyperShot 0.2.x for Minecraft Java 26.2 / Fabric / Java 25
Design branch: `fix/hypershot-capture-ui-0.2.1`
Depends on: `2026-08-08-hypershot-capture-ui-redesign.md`

## Purpose

HyperShot should evolve from an extreme-resolution screenshot utility into a coherent in-game photography tool. The user should be able to take a normal screenshot instantly, or deliberately compose a cinematic shot with the same kind of confidence and clarity expected from a modern camera app.

The product must remain truthful about Minecraft's limits. Client-side camera controls work everywhere. World-control features only appear as available where HyperShot can safely control them, primarily singleplayer/integrated-server sessions. Multiplayer must never pretend the client can freeze or authoritatively rewrite a server-controlled world.

This design adds a hybrid camera viewfinder, photography modes, timer/countdown, composition guides, shader-settle preparation, burst capture, time-of-day bracketing, singleplayer cinematic scene controls, queued-shot readiness, and reusable scene presets. It also defines how these features fit into the existing extreme-resolution, preflight, gallery, and restoration architecture.

## Product principles

1. **Fast when you want fast.** A normal screenshot remains one action.
2. **Powerful when you deliberately enter camera mode.** Advanced photographic tools do not block ordinary F2 use.
3. **The scene stays visible.** The camera UI is a lightweight viewfinder first, not a settings screen covering the world.
4. **Modes represent genuinely different capture behavior.** Shared options such as resolution, timer, guides, shader settle, HUD hiding, and FOV remain modifiers rather than becoming separate modes.
5. **No fake control.** Multiplayer/server-owned behavior is shown as unavailable with an explanation.
6. **No silent world edits.** Cinematic scene changes are temporary, explicit, and restored on finish, cancel, error, screen close, or world unload.
7. **Extreme resolution remains safety-gated.** Burst, bracketing, and cinematic sequencing multiply workload and must feed into the same preflight system as 8K/16K/32K.
8. **Shader compatibility is best-effort and explicit.** HyperShot can wait for shader history/exposure to settle, but it must not claim universal convergence or perfect tiled compatibility.

## F2 interaction model

F2 behavior is configurable. The default is:

- **Tap F2:** take an instant HyperShot screenshot using the last active capture settings.
- **Hold F2:** enter the HyperShot camera viewfinder.

Settings may change this to:

- always instant;
- always open viewfinder;
- tap instant / hold viewfinder;
- user-defined alternate keybind for the viewfinder.

A hold threshold should be long enough to avoid accidental camera-mode entry but short enough to feel responsive. The exact default threshold belongs in implementation settings, not hard-coded UI copy.

Instant F2 never waits for the full camera UI. If the active preset itself requires a mandatory safety confirmation or is blocked by preflight, HyperShot shows the relevant failure/confirmation instead of silently starting an unsafe capture.

## Hybrid camera viewfinder

### Default state

Holding F2 opens an unobtrusive in-world viewfinder. Minecraft remains the visual focus.

The collapsed viewfinder shows only high-value information:

- active mode;
- output resolution shorthand and exact dimensions on hover/details;
- output format;
- current shot-readiness state;
- timer state;
- shader-settle state when relevant;
- composition-guide state;
- compact capture/preflight status;
- shutter/capture action;
- control-drawer toggle.

The UI should fade down while the user is composing and become fully visible again when the mouse moves, a control is focused, or a warning/readiness transition needs attention.

### Expanded control drawer

A small camera-controls button expands a secondary drawer without leaving the world. The drawer contains mode-specific options plus shared photography modifiers.

The drawer must not become a second copy of the Mod Menu settings screen. Deep technical controls such as tile overlap, encoder internals, and disk reserve remain in Advanced settings unless directly necessary for the current shot.

### Mode strip

The camera viewfinder exposes four top-level capture modes:

- **Photo**
- **Burst**
- **Time**
- **Cinematic**

The mode strip can be navigated with mouse/keyboard and should preserve shared modifiers when switching modes unless a modifier is invalid for the new mode.

## Shared photography modifiers

These are not top-level modes. They can apply to one or more modes where sensible.

### Timer / countdown

Built-in choices:

- Off
- 3 seconds
- 5 seconds
- 10 seconds
- Custom

Behavior:

1. User presses capture.
2. HyperShot enters `WAITING FOR TIMER` if a delay is configured.
3. A subtle visual countdown appears in the viewfinder.
4. Optional non-intrusive countdown sound cues may be enabled in settings.
5. The viewfinder may fade almost completely during the final second so the player can judge composition without UI clutter.
6. The timer completes before the actual capture sequence begins.

For tiled 8K/16K/32K captures, countdown is a preparation phase, not something repeated per tile.

Cancelling camera mode cancels an active countdown immediately.

### Composition guides

Guides are viewfinder-only and must never appear in output images.

Initial guide set:

- Off
- Rule of Thirds
- Center Cross
- Golden Ratio
- Horizon
- Diagonal
- Safe Frame

Additional viewfinder aids:

- adjustable guide opacity;
- optional camera-level indicator showing when pitch/roll is close to level;
- optional aspect framing overlays such as 16:9, 21:9, 4:3, 1:1, and phone/vertical framing.

Aspect framing overlays are composition aids only unless the user separately chooses a matching output/crop resolution.

Guides should use simple, crisp lines and avoid excessive animation.

### Shader Settle

Shader Settle is a shot-preparation system intended for shader packs with temporal history, exposure adaptation, noise accumulation, or other effects that look wrong immediately after a camera/time/weather change.

User-facing modes:

- Off
- Quick
- Standard
- Deep
- Custom

The labels represent wait profiles rather than promises about a particular shader implementation.

Behavior:

1. If the shot or scene configuration changes in a way likely to invalidate shader history, HyperShot starts a settle countdown.
2. Readiness becomes `SETTLING SHADERS`.
3. The viewfinder displays remaining settle time.
4. Capture automatically continues after the wait if the shot was already queued.
5. The wait is performed before the tiled render begins.
6. HyperShot should not re-warm between every tile unless a proven renderer/shader constraint makes that necessary.

If Iris or another supported shader indicator can be detected safely through public APIs/metadata, HyperShot may recommend Standard automatically. Detection is advisory, not required for the feature.

The UI must say that shader settle is a best-effort wait and does not guarantee perfect convergence or tile-safe shader behavior.

### Appearance modifiers

Shared visual modifiers include:

- Hide HUD
- Hide hand
- Hide block outline
- Hide name tags where safely achievable client-side
- Hide selected particle categories where safely achievable
- Lock FOV for the shot

A **Clean Frame** convenience preset enables the conservative visual-only cleanup options without changing the world itself.

## Shot-readiness state machine

The camera viewfinder exposes one primary readiness state at a time. The detailed drawer can list all outstanding conditions.

States:

- `READY`
- `WAITING FOR TIMER`
- `SETTLING SHADERS`
- `WAITING FOR CHUNKS`
- `PREPARING SCENE`
- `HIGH LOAD`
- `BLOCKED`
- `CAPTURING`
- `FINALIZING`
- `CANCELLED`
- `FAILED`

A capture command may become a **queued shot** rather than firing immediately.

Example:

- timer = 3 seconds;
- chunks not ready;
- shaders settling.

Pressing the shutter produces a queued shot with prerequisites. HyperShot automatically progresses through preparation and fires once mandatory prerequisites are satisfied.

The user can cancel a queued shot at any time.

`HIGH LOAD` is not itself a mandatory block unless the underlying safety preflight says so. `BLOCKED` means at least one condition prevents capture entirely.

## Photo mode

Photo is the default camera mode.

It uses the current resolution/format/preset and shared modifiers. It can be used for Native, 4K, 8K, 16K, 32K, or Custom captures.

Photo mode is the baseline for:

- timer;
- composition guides;
- shader settle;
- clean-frame options;
- FOV lock;
- queued shot readiness;
- extreme-resolution preflight.

No additional scene sequencing occurs unless Cinematic or another sequence mode is selected.

## Burst mode

Burst captures multiple frames from the current scene.

### Controls

Initial shot-count choices:

- 3
- 5
- 10
- Custom

Initial interval choices:

- Every available frame
- 100 ms
- 250 ms
- 500 ms
- 1 second
- Custom

For very high-resolution tiled captures, HyperShot must not call the sequence a high-speed burst if each frame requires substantial render/finalization time. The UI should describe it as a **capture sequence** when appropriate.

### Workload calculation

Preflight multiplies the relevant per-shot estimates by shot count and accounts for temporary-storage overlap between shots where implementation requires it.

Examples that should trigger strong warnings:

- 10 × 16K PNG;
- 10 × 32K PNG;
- long bursts combined with Deep Shader Settle;
- burst capture where disk reserve would be exhausted.

`CANNOT START` blocks the whole burst before the first shot.

### Gallery behavior

Burst outputs are grouped into one Gallery stack rather than appearing as unrelated files.

The stack view should support:

- contact-sheet thumbnails;
- favorite/best-frame selection;
- open individual frame;
- delete individual frame;
- delete the stack;
- retain metadata linking all frames to the same burst session.

HyperShot does not automatically delete non-selected burst frames.

## Time mode / time-of-day bracketing

Time mode captures one composition under multiple world-lighting states.

### Availability

Authoritative time bracketing is available only when HyperShot can safely control the local singleplayer/integrated-server world state.

On multiplayer servers, Time mode remains visible but disabled with an explanation that the server controls world time.

### Built-in lighting sequence

The initial curated sequence is:

- Sunrise
- Morning
- Noon
- Golden Hour
- Sunset
- Blue Hour
- Night

The exact Minecraft tick values must be implementation-defined and tested against Minecraft 26.2 lighting behavior rather than guessed in the design document.

### Custom bracket

Advanced Time mode supports:

- start world time;
- end world time;
- step size;
- optional wrap across day boundary;
- optional selection of only named lighting states.

### Sequence behavior

1. Record exact original world time and relevant scene state.
2. Apply the first bracket state.
3. Wait for chunks/shaders if configured.
4. Capture.
5. Repeat for each bracket state.
6. Restore the original scene state even after cancellation or failure.

Weather may be locked for the sequence so lighting comparison is not polluted by an unexpected weather change.

Time-mode outputs are grouped in Gallery as one bracket stack with the lighting-state label stored in per-frame metadata.

## Cinematic mode

Cinematic mode is the singleplayer scene-control layer. It combines camera preparation, optional world-state control, and the existing capture pipeline.

### Core rule

Cinematic mode never silently modifies the world. Every scene-control option is explicit, temporary, and covered by restoration.

### Singleplayer-only scene controls

Where technically safe in Minecraft 26.2, Cinematic mode may expose:

- **Freeze world** — stop or pause local simulation elements needed to keep the scene stable while leaving the render/camera loop operational.
- **Lock time** — preserve the current sun/moon position or use a named photographic time.
- **Lock weather** — preserve current weather or temporarily request Clear / Rain / Thunder.
- **Wait for chunks** — delay the shot until the visible/local scene reaches a defined readiness threshold or a timeout expires.
- **Freeze camera position** — lock camera position, yaw, and pitch while composing/capturing.
- **Freeze FOV** — prevent transient FOV changes from altering framing.
- **Shader settle** — reuse the shared preparation system after relevant scene changes.
- **Hide distractions** — client-side visual cleanup such as HUD, hand, outline, name tags, and supported particles/entities where this can be done without unsafe world mutation.

### Freeze-world implementation constraint

The design requires scene stability, not a reckless global pause patch. Implementation must identify the narrowest safe mechanism available in Minecraft 26.2.

If fully pausing integrated-server simulation would also break rendering, networking, chunk completion, or restoration guarantees, HyperShot must use a narrower freeze strategy or mark the control unavailable rather than hacking around those invariants.

The UI copy should describe exactly what is frozen once implementation behavior is known.

### Camera lock

Camera lock freezes the camera transform used for the shot. The user must always have an immediate escape/unlock path.

Leaving the world, closing camera mode, cancelling the shot, or encountering an error clears the lock.

### Chunk readiness

`WAITING FOR CHUNKS` is best-effort and client-observable.

The first implementation should use measurable local readiness signals available in Minecraft 26.2 rather than claiming certainty about terrain the client has not received.

A configurable timeout prevents indefinite waiting. On timeout, the user may:

- capture anyway, if safety permits;
- keep waiting;
- cancel.

### Multiplayer behavior

On multiplayer servers, these controls are unavailable because the server owns them:

- freeze world;
- authoritative time control;
- authoritative weather control;
- freezing server entities/simulation.

These remain available where client-side only:

- timer;
- guides;
- shader settle;
- burst;
- camera lock;
- FOV lock;
- HUD/hand/outline/name-tag hiding where supported;
- client-observable chunk readiness;
- high-resolution capture.

Unavailable controls stay visible but disabled with a concise explanation such as `Singleplayer only — the server controls world time.`

## Scene restoration system

Cinematic and Time modes require one shared restoration subsystem.

### Restoration snapshot

Before applying temporary scene changes, HyperShot records only the state it intends to modify, such as:

- world time;
- weather state;
- simulation/freeze state;
- camera lock state;
- FOV state;
- temporary visibility overrides;
- any mode-owned shader/chunk preparation state.

### Restoration triggers

Restoration is attempted on:

- successful completion;
- user cancel;
- capture failure;
- exception during preparation;
- closing camera mode;
- disconnect/world unload;
- client shutdown where the state is still locally restorable.

Restoration must be idempotent. Calling it twice must not corrupt state.

If restoration itself fails, HyperShot logs the original capture error and restoration error separately and shows a visible warning.

Persistent recovery metadata should not claim it can restore a world across a process crash unless the specific state is actually recoverable after restart.

## Scene Snapshots

A Scene Snapshot is a reusable photography setup preset, not a world save and not a screenshot.

It may store:

- mode;
- resolution;
- output format;
- timer;
- guide type and opacity;
- shader-settle profile;
- FOV / FOV lock preference;
- appearance modifiers;
- time-mode sequence choice;
- cinematic time/weather preferences;
- chunk-wait preference;
- clean-frame options.

By default it does **not**:

- teleport the player;
- restore a world position;
- rewrite world blocks/entities;
- silently change server state.

Camera-position bookmarks may be added later as a separate opt-in feature, but are not required by this design.

Scene Snapshots can be named, duplicated, renamed, and deleted from the camera/settings UI. A small set of built-in examples may ship, such as Landscape, Wallpaper, Cinematic Sunset, and Social, as long as they remain editable copies rather than magical hard-coded modes.

## UI design

### Visual language

The camera UI should feel like a polished Minecraft-native photography overlay, not a mobile-app skin pasted over the game.

- Use Minecraft interaction conventions for focus, buttons, narration, and key navigation.
- Keep the center of the screen visually quiet.
- Avoid large opaque panels over the scene.
- Avoid excessive pills, borders, and decorative cards.
- Use strong selected/disabled states.
- Keep warnings readable without making the whole screen red/orange.
- Preserve usability at small GUI scales through reflow and compact control groups.

### Collapsed viewfinder information hierarchy

Top/edge status:

- HyperShot / active mode;
- resolution + format;
- readiness state.

Lower controls:

- timer;
- shader settle;
- guides;
- mode strip;
- shutter;
- drawer toggle.

Composition guides occupy the scene area but remain visually subtle.

### Expanded drawer sections

The expanded drawer is mode-sensitive.

**Shared**

- resolution;
- PNG/JPEG;
- timer;
- shader settle;
- guides;
- FOV;
- appearance.

**Burst**

- frame count;
- interval;
- sequence workload.

**Time**

- curated lighting sequence or custom range;
- weather lock;
- settle between states.

**Cinematic**

- world freeze;
- time;
- weather;
- chunk wait;
- camera lock;
- FOV lock;
- clean frame;
- restoration summary.

A one-line `What will change?` summary should be available before a Cinematic shot starts.

### Readiness presentation

The primary status is concise:

- READY
- WAITING FOR TIMER
- SETTLING SHADERS
- WAITING FOR CHUNKS
- HIGH LOAD
- BLOCKED

Opening details reveals exact blockers/waits.

A queued shot should say what it is waiting for rather than leaving the user wondering why the shutter did nothing.

## Configuration model

Camera settings should be separated into these conceptual groups:

- **Capture output** — resolution, format, encoder-related preset fields.
- **Photography modifiers** — timer, guide, settle, FOV, appearance.
- **Mode state** — Photo/Burst/Time/Cinematic and mode-specific options.
- **Interaction preferences** — F2 tap/hold behavior, hold threshold, viewfinder fade behavior.
- **Scene Snapshots** — named reusable setups.

The configuration schema must migrate existing HyperShot users without deleting their current capture presets.

Existing 4K/8K/16K/32K presets remain compatible with the camera UI.

## Capture orchestration architecture

The new features should not be implemented as ad-hoc conditionals inside `CaptureManager`.

Introduce a shot-orchestration layer responsible for preparation and sequencing. Suggested conceptual units:

### ShotRequest

Immutable description of one requested photographic action:

- active mode;
- one or more capture requests/frames;
- preparation requirements;
- shared modifiers;
- restoration requirements;
- group/session metadata.

### ShotCoordinator

Owns the high-level state machine:

- queue shot;
- validate preflight;
- apply scene preparation;
- wait for timer/chunks/shaders;
- trigger one capture;
- advance burst/bracket sequence;
- finalize group metadata;
- restore scene;
- handle cancel/failure.

`CaptureManager` remains focused on rendering/reading/encoding one image request rather than becoming responsible for timers, world time, burst sequencing, or viewfinder state.

### SceneController

Singleplayer-aware abstraction for temporary world/camera state.

It reports capabilities before exposing controls. Multiplayer implementations return unavailable for server-owned controls.

### ShotReadiness

Pure/readable model consumed by the viewfinder. It reports current state, blockers, waits, and progress without letting the UI directly mutate capture internals.

### CaptureGroup

Shared metadata identity for Burst and Time sequences so Gallery can group related outputs.

## Error handling

### Preparation failures

If a prerequisite fails before capture begins:

- no image capture starts;
- temporary scene changes are restored;
- queued-shot state becomes `FAILED` or `BLOCKED` with a readable reason;
- technical details are logged.

### Mid-sequence failures

For Burst/Time sequences:

- stop the remaining sequence by default;
- preserve successfully completed images;
- group metadata marks the sequence incomplete;
- restore the scene;
- allow the Gallery to show which frames completed.

Do not silently discard successful frames because a later frame failed.

### CaptureManager failures

Existing capture failure handling remains authoritative for per-image rendering/encoding errors. The ShotCoordinator observes the failure, stops the higher-level sequence, and restores scene state.

### World unload/disconnect

Any queued or active sequence is cancelled. Client-only camera locks and visual overrides are cleared immediately. Server-owned restoration is not attempted after the world object is gone.

## Shader and renderer compatibility

- No direct raw OpenGL/LWJGL path is introduced by this design.
- Shader Settle is timing/readiness logic, not shader-pack code injection.
- HyperShot must not patch Iris/Sodium internals by name simply to expose a camera feature.
- A renderer/shader combination that cannot safely support tiled projection may still block extreme-resolution capture with a compatibility error.
- Native/moderate-resolution screenshots may remain usable even when an extreme tiled mode is unavailable.
- Time/weather changes followed by Shader Settle are best-effort; the UI must not label them as physically accurate exposure bracketing.

## Preflight changes for sequences

The existing preflight model expands to account for mode multipliers.

For each shot session calculate:

- per-frame output size;
- frame count;
- total output pixels across the sequence;
- estimated total final bytes;
- worst-case temporary storage;
- peak heap estimate;
- tile count per frame and total tile work;
- settle/timer delay contribution;
- safety classification.

For a sequence, safety is based on the whole planned session, not only the first frame.

Example UI copy:

`10-shot 32K sequence • ~5.31 billion output pixels • HIGH LOAD`

The system must use checked arithmetic throughout.

## Gallery integration

Gallery gains capture-group awareness.

Group types:

- Burst
- Time Bracket

A group stores:

- group/session ID;
- mode;
- created time;
- shared Scene Snapshot/settings summary;
- ordered frame IDs;
- completion state;
- per-frame labels such as `Golden Hour` or `Burst 4/10`.

The normal single-photo Gallery experience remains unchanged.

## Accessibility and controls

- All viewfinder controls must be keyboard navigable.
- Important state changes must have text/narration equivalents; color alone is insufficient.
- Composition guides need adjustable opacity and an off state.
- Countdown sound is optional.
- Reduced-motion behavior should avoid unnecessary fading/animation where possible.
- The shutter, cancel, unlock-camera, and close-viewfinder actions must always be reachable without a mouse.

## Testing strategy

### Pure/unit tests

Add tests for:

- timer state transitions;
- queued-shot prerequisite ordering;
- cancel during timer;
- cancel during shader settle;
- cancel while waiting for chunks;
- sequence frame counting;
- Burst workload multiplication;
- Time bracket ordering;
- checked arithmetic for 32K × burst counts;
- ShotReadiness priority when multiple conditions are pending;
- restoration snapshot idempotence;
- capability gating for singleplayer vs multiplayer;
- Scene Snapshot serialization/migration;
- capture-group metadata;
- compact/wide viewfinder layout math where separable from Minecraft rendering.

### Integration/client tests

Automated Minecraft client verification should cover, where practical:

- viewfinder opens and closes without corrupting input;
- tap/hold F2 dispatch logic;
- guides do not appear in captured output;
- camera lock clears on close/cancel;
- queued Photo capture fires after a short timer;
- Burst sequence produces the requested number of low-resolution frames in a test environment;
- Time/Cinematic controls are unavailable in a multiplayer-context test double or guarded runtime path;
- scene restoration executes after an injected capture failure.

### Real hardware tests

Because shaders, tile capture, and macOS rendering cannot be fully proven in headless CI, release testing should include:

1. Native Photo, no shaders.
2. 4K Photo, no shaders.
3. 8K tiled Photo, no shaders.
4. Native/4K Photo with a common Iris shader pack and Standard Shader Settle.
5. Timer + guides; confirm guides are absent from output.
6. 3-shot Native Burst.
7. Singleplayer Time bracket; confirm original time restores exactly.
8. Singleplayer Cinematic shot with at least camera lock + time/weather lock; confirm restoration.
9. Failure/cancel during Cinematic preparation; confirm restoration.
10. 16K tiled capture after the camera-mode layer is enabled.
11. 32K remains experimental until a complete 30,720 × 17,280 output is verified on real hardware.

## Release scope and sequencing

This design is one feature family but should be implemented in reviewable phases:

### Phase 1 — Camera shell and foundational modifiers

- F2 tap/hold behavior;
- hybrid viewfinder;
- Photo mode;
- Timer;
- Composition Guides;
- Shader Settle;
- ShotReadiness / queued-shot orchestration;
- clean separation between ShotCoordinator and CaptureManager.

### Phase 2 — Sequence modes

- Burst;
- Time Bracketing;
- capture-group metadata;
- Gallery stack/contact-sheet support;
- sequence-aware preflight.

### Phase 3 — Cinematic scene controls

- SceneController capability model;
- camera/FOV lock;
- time/weather control;
- chunk readiness;
- freeze-world mechanism if a safe 26.2 implementation is proven;
- restoration subsystem;
- Clean Frame;
- multiplayer disabled-state UX.

### Phase 4 — Scene Snapshots and final polish

- reusable Scene Snapshots;
- built-in photography examples;
- viewfinder refinement;
- accessibility/reduced motion;
- final documentation and hardware verification.

Each phase must preserve a usable Photo mode and must not require unfinished later phases to keep the mod stable.

## Non-goals for this design

- Physical camera simulation such as real aperture/depth-of-field optics independent of shaders.
- Fake HDR/EXR/16-bit output.
- Automatic AI composition scoring.
- Video recording.
- Full replay/timeline editor.
- Camera-path keyframe animation/orbit mode.
- Teleporting the player as part of Scene Snapshots.
- Modifying arbitrary shader-pack settings.
- Pretending client-side multiplayer time/weather changes are authoritative.
- Guaranteeing every shader pack can render correctly in tiled 16K/32K mode.

These may be considered later only after the photography core is stable.

## Acceptance criteria

The camera polish is considered implemented only when:

- tap F2 still supports a fast screenshot path;
- hold F2 opens the hybrid viewfinder without obscuring the scene;
- Photo mode works with existing capture presets;
- timer, guides, and shader settle behave as designed;
- guides never contaminate output;
- readiness clearly explains why a queued shot is waiting;
- Burst and Time sequences are preflighted as whole sessions and grouped in Gallery;
- singleplayer Cinematic controls are capability-gated and restored after success, cancel, and failure;
- multiplayer never exposes server-owned controls as if they worked;
- CaptureManager remains focused on per-image capture while higher-level sequencing lives outside it;
- automated tests pass;
- the existing Java 25 final-render-target crash regression guard remains intact;
- real hardware tests validate the capture path before any final-release claim;
- 32K remains explicitly experimental until a full 30,720 × 17,280 capture is verified.
