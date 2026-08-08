# HyperShot Camera Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first usable HyperShot camera layer: configurable tap/hold F2 behavior, a hybrid in-world Photo viewfinder, Timer, Composition Guides, Shader Settle, and a queued-shot/readiness coordinator that still delegates one-image rendering to the existing `CaptureManager`.

**Architecture:** Keep `CaptureManager` responsible for one rendered/encoded image. Add pure camera/shot state models under `src/main/java/dev/hypershot/core/camera`, then a client-only `ShotCoordinator` that sequences timer/settle/preflight and calls `CaptureManager.start`. Add a non-pausing `CameraViewfinderScreen` as a lightweight overlay; it never renders into the screenshot because it suppresses its own extraction while a HyperShot capture pass is active.

**Tech Stack:** Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Java 25, Gradle 9.5.1, Loom 1.17.17, Mod Menu 20.0.1, Sponge Mixin, existing dependency-free core test runner plus JUnit/Gradle build.

## Global Constraints

- Minecraft target stays exactly `26.2` and Java stays exactly `25`.
- Do not reintroduce any write to Minecraft/GameRenderer's final main render target.
- No direct raw LWJGL/OpenGL capture path.
- Tap F2 must remain the fast path; hold F2 opens the camera viewfinder by default.
- Photo mode is the only top-level mode implemented in Phase 1; Burst, Time, and Cinematic remain visible as later-phase/disabled entries only if that does not confuse the UI.
- Timer choices: Off, 3s, 5s, 10s, Custom.
- Guide choices: Off, Rule of Thirds, Center Cross, Golden Ratio, Horizon, Diagonal, Safe Frame.
- Shader Settle choices: Off, Quick, Standard, Deep, Custom; labels are wait profiles, not compatibility guarantees.
- Guides/viewfinder chrome must never appear in captured image output regardless of the preset's HUD setting.
- Shader settle runs before the tiled capture begins, not per tile.
- `CaptureManager` must remain per-image capture code; timer/viewfinder/readiness orchestration lives outside it.
- Existing 4K/8K/16K/32K safety preflight remains authoritative; 32K stays experimental until real hardware validation.
- Existing 0.2.1 Java-25 render-target regression guards must remain green.

---

## File Structure

### New pure/core files

- `src/main/java/dev/hypershot/core/camera/CameraMode.java` — top-level camera mode enum; Phase 1 operates `PHOTO` only.
- `src/main/java/dev/hypershot/core/camera/F2Behavior.java` — F2 interaction policy enum.
- `src/main/java/dev/hypershot/core/camera/GuideType.java` — composition guide enum.
- `src/main/java/dev/hypershot/core/camera/ShaderSettleProfile.java` — predefined wait profiles plus custom-resolution helper.
- `src/main/java/dev/hypershot/core/camera/ShotReadinessState.java` — primary readable state enum.
- `src/main/java/dev/hypershot/core/camera/ShotReadiness.java` — immutable state/reason/time model consumed by UI.
- `src/main/java/dev/hypershot/core/camera/ShotPreparationMachine.java` — pure timer -> shader-settle -> ready state machine.
- `src/main/java/dev/hypershot/core/camera/GuideGeometry.java` — normalized line geometry for guide rendering and layout tests.

### New client files

- `src/client/java/dev/hypershot/capture/CaptureListenerHub.java` — fan-out listener so notifications and shot coordinator observe one capture.
- `src/client/java/dev/hypershot/shot/ShotCoordinator.java` — client state coordinator; owns queued Photo shot and reacts to capture completion/failure.
- `src/client/java/dev/hypershot/input/F2GestureController.java` — detects screenshot-key press/hold/release using the user's actual Minecraft screenshot key mapping.
- `src/client/java/dev/hypershot/ui/CameraViewfinderScreen.java` — non-pausing hybrid Photo viewfinder.
- `src/client/java/dev/hypershot/ui/CameraSettingsScreen.java` — focused camera interaction/default-modifier settings instead of bloating `SettingsScreen`.

### Existing files to modify

- `src/client/java/dev/hypershot/HyperShotClient.java` — construct coordinator/listener hub/F2 controller, tick them, expose camera entry points.
- `src/client/java/dev/hypershot/config/HyperShotConfig.java` — schema 3 camera settings + migration.
- `src/client/java/dev/hypershot/mixin/ScreenshotMixin.java` — suppress vanilla F2 only; no longer immediately starts HyperShot capture when tap/hold behavior is enabled.
- `src/client/java/dev/hypershot/ui/SettingsScreen.java` — route to Camera Settings without adding technical camera state to the main simple screen.
- `src/client/resources/assets/hypershot/lang/en_us.json` — labels/tooltips/readiness copy.
- `src/test/java/dev/hypershot/core/CoreTestMain.java` — dependency-free red/green tests for state machine/geometry.
- `src/test/java/dev/hypershot/core/UiLayoutTest.java` — add guide/viewfinder layout math tests where useful.
- `tools/verify-project.sh` — source guard that viewfinder suppression and render-target regression remain present.
- `CHANGELOG.md` — Phase 1 development entry after implementation is green.
- `docs/KNOWN_LIMITATIONS.md` — shader-settle wording and Phase 1 mode availability.

---

### Task 1: Add pure camera enums and preparation state machine

**Files:**
- Create: `src/main/java/dev/hypershot/core/camera/CameraMode.java`
- Create: `src/main/java/dev/hypershot/core/camera/F2Behavior.java`
- Create: `src/main/java/dev/hypershot/core/camera/GuideType.java`
- Create: `src/main/java/dev/hypershot/core/camera/ShaderSettleProfile.java`
- Create: `src/main/java/dev/hypershot/core/camera/ShotReadinessState.java`
- Create: `src/main/java/dev/hypershot/core/camera/ShotReadiness.java`
- Create: `src/main/java/dev/hypershot/core/camera/ShotPreparationMachine.java`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java`

**Interfaces:**
- Consumes: monotonic `long nowNanos`, timer duration, shader-settle duration.
- Produces: `ShotPreparationMachine.start(long nowNanos, long timerNanos, long settleNanos)`, `noteSceneChanged(long nowNanos)`, `tick(long nowNanos)`, `cancel()`, and `ShotReadiness readiness(long nowNanos)`.

- [ ] **Step 1: Write failing state-machine tests**

Add a `cameraPreparation()` method to `CoreTestMain.main()` with assertions equivalent to:

```java
ShotPreparationMachine machine = new ShotPreparationMachine();
machine.start(0L, 3_000_000_000L, 1_000_000_000L);
eq(ShotReadinessState.WAITING_FOR_TIMER, machine.readiness(0L).state(), "timer starts first");
machine.tick(3_000_000_000L);
eq(ShotReadinessState.SETTLING_SHADERS, machine.readiness(3_000_000_000L).state(), "settle follows timer");
machine.noteSceneChanged(3_500_000_000L);
machine.tick(4_000_000_000L);
eq(ShotReadinessState.SETTLING_SHADERS, machine.readiness(4_000_000_000L).state(), "camera movement restarts settle");
machine.tick(4_500_000_000L);
eq(ShotReadinessState.READY, machine.readiness(4_500_000_000L).state(), "ready after settle");
machine.cancel();
eq(ShotReadinessState.CANCELLED, machine.readiness(4_500_000_000L).state(), "cancel terminal state");
```

Also assert `ShaderSettleProfile.QUICK.defaultMillis() == 250`, `STANDARD == 1000`, `DEEP == 2500`, and custom duration clamps to `0..10_000 ms`.

- [ ] **Step 2: Run the core suite and verify RED**

Run:

```bash
./tools/run-core-tests.sh
```

Expected: compilation failure because the new camera classes do not exist.

- [ ] **Step 3: Implement the minimal pure models**

Use these enums/signatures:

```java
public enum CameraMode { PHOTO, BURST, TIME, CINEMATIC }
public enum F2Behavior { INSTANT_ONLY, VIEWFINDER_ONLY, TAP_INSTANT_HOLD_VIEWFINDER }
public enum GuideType { OFF, RULE_OF_THIRDS, CENTER_CROSS, GOLDEN_RATIO, HORIZON, DIAGONAL, SAFE_FRAME }
public enum ShotReadinessState { READY, WAITING_FOR_TIMER, SETTLING_SHADERS, HIGH_LOAD, BLOCKED, CAPTURING, FINALIZING, CANCELLED, FAILED }
```

`ShotPreparationMachine` must use monotonic time only, transition in the order `WAITING_FOR_TIMER -> SETTLING_SHADERS -> READY`, and restart the settle deadline when `noteSceneChanged()` is called during the settle phase.

- [ ] **Step 4: Run the core suite and verify GREEN**

Run `./tools/run-core-tests.sh`.

Expected: all previous assertions plus the new camera preparation assertions pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hypershot/core/camera src/test/java/dev/hypershot/core/CoreTestMain.java
git commit -m "feat: add camera shot preparation state machine"
```

---

### Task 2: Add deterministic composition-guide geometry

**Files:**
- Create: `src/main/java/dev/hypershot/core/camera/GuideGeometry.java`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java`

**Interfaces:**
- Consumes: `GuideType`, viewport width/height, safe-frame inset fraction.
- Produces: `List<GuideGeometry.Line> lines(GuideType type, int width, int height)` where `Line` stores integer `x1,y1,x2,y2`.

- [ ] **Step 1: Write failing geometry tests**

Test exact positions for a `900x600` viewport:

```java
var thirds = GuideGeometry.lines(GuideType.RULE_OF_THIRDS, 900, 600);
eq(4, thirds.size(), "thirds line count");
eq(new GuideGeometry.Line(300, 0, 300, 600), thirds.get(0), "thirds first vertical");
eq(new GuideGeometry.Line(600, 0, 600, 600), thirds.get(1), "thirds second vertical");

var cross = GuideGeometry.lines(GuideType.CENTER_CROSS, 900, 600);
eq(2, cross.size(), "center cross line count");

var off = GuideGeometry.lines(GuideType.OFF, 900, 600);
eq(0, off.size(), "off has no lines");
```

Also verify Golden Ratio, Horizon, Diagonal, and Safe Frame stay within viewport bounds.

- [ ] **Step 2: Run RED**

Run `./tools/run-core-tests.sh` and confirm missing `GuideGeometry` causes failure.

- [ ] **Step 3: Implement geometry only**

Use integer deterministic math; do not depend on Minecraft classes. Safe Frame is a 5% inset rectangle. Golden-ratio lines use approximately `0.382` and `0.618` of width/height.

- [ ] **Step 4: Run GREEN**

Run `./tools/run-core-tests.sh` and confirm all guide assertions pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hypershot/core/camera/GuideGeometry.java src/test/java/dev/hypershot/core/CoreTestMain.java
git commit -m "feat: add composition guide geometry"
```

---

### Task 3: Migrate configuration to schema 3 camera settings

**Files:**
- Modify: `src/client/java/dev/hypershot/config/HyperShotConfig.java`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java` for pure enum/profile validation where applicable.

**Interfaces:**
- Consumes: enums from Task 1.
- Produces public config fields used by input/coordinator/UI:

```java
public F2Behavior f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
public int f2HoldThresholdMs = 350;
public CameraMode cameraMode = CameraMode.PHOTO;
public int timerSeconds = 0;
public GuideType guideType = GuideType.RULE_OF_THIRDS;
public float guideOpacity = 0.45f;
public ShaderSettleProfile shaderSettleProfile = ShaderSettleProfile.STANDARD;
public int customShaderSettleMs = 1000;
public boolean cameraOverlayFade = true;
public boolean countdownSounds = true;
```

- [ ] **Step 1: Add migration expectations**

Add source/config test coverage sufficient to prove defaults are valid and schema 2 users gain camera defaults without losing presets. If direct Gson config tests require client-only dependencies, add a source guard in `tools/verify-project.sh` that checks `CURRENT_SCHEMA = 3` and the new fields while leaving runtime migration validation to Gradle/JUnit.

- [ ] **Step 2: Run RED**

Run `./tools/verify-project.sh`; expected failure after the guard is added because schema 3 fields are absent.

- [ ] **Step 3: Implement schema 3 migration**

Set `CURRENT_SCHEMA = 3`. In `migrateAndValidate()`:

```java
if (schemaVersion < 3) {
    f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
    cameraMode = CameraMode.PHOTO;
    timerSeconds = 0;
    guideType = GuideType.RULE_OF_THIRDS;
    guideOpacity = 0.45f;
    shaderSettleProfile = ShaderSettleProfile.STANDARD;
    customShaderSettleMs = 1000;
    cameraOverlayFade = true;
    countdownSounds = true;
}
```

Clamp hold threshold to `150..1000 ms`, timer to `0..60 s`, opacity to `0.10..1.0`, custom settle to `0..10_000 ms`; null enums restore defaults. Preserve all existing capture presets.

- [ ] **Step 4: Run GREEN**

Run `./tools/verify-project.sh` and `./tools/run-core-tests.sh`.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/dev/hypershot/config/HyperShotConfig.java tools/verify-project.sh src/test/java/dev/hypershot/core/CoreTestMain.java
git commit -m "feat: add camera configuration schema"
```

---

### Task 4: Add listener fan-out without changing CaptureManager ownership

**Files:**
- Create: `src/client/java/dev/hypershot/capture/CaptureListenerHub.java`
- Modify: `src/client/java/dev/hypershot/HyperShotClient.java`

**Interfaces:**
- Consumes: existing `CaptureListener`.
- Produces: `add(CaptureListener)`, `remove(CaptureListener)`, and fan-out implementations of all five listener callbacks.

- [ ] **Step 1: Add a focused JUnit/client test or source-level verification guard**

Guard that `HyperShotClient` installs exactly one listener hub into `CaptureManager` and adds notifications through the hub instead of replacing the listener later.

- [ ] **Step 2: Run RED**

Run `./tools/verify-project.sh`; expected guard failure while `CaptureListenerHub` is absent.

- [ ] **Step 3: Implement the hub**

Use `CopyOnWriteArrayList<CaptureListener>`. Each callback iterates listeners; one listener exception is logged/isolated and must not prevent the other listener from receiving the event.

- [ ] **Step 4: Wire `HyperShotClient`**

Create hub before `CaptureManager.setListener`; add the existing notification manager to it. Do not change `CaptureManager`'s public listener API in this task.

- [ ] **Step 5: Run GREEN and commit**

Run `./tools/verify-project.sh` and `./gradlew test --no-daemon`.

```bash
git add src/client/java/dev/hypershot/capture/CaptureListenerHub.java src/client/java/dev/hypershot/HyperShotClient.java tools/verify-project.sh
git commit -m "refactor: fan out capture events safely"
```

---

### Task 5: Add ShotCoordinator for queued Photo capture

**Files:**
- Create: `src/client/java/dev/hypershot/shot/ShotCoordinator.java`
- Modify: `src/client/java/dev/hypershot/HyperShotClient.java`
- Modify: `src/client/java/dev/hypershot/capture/CaptureManager.java` only if a read-only preflight accessor is needed; do not move timer logic into it.

**Interfaces:**
- Consumes: `CaptureManager`, `HyperShotConfig`, `CaptureListenerHub`, `ShotPreparationMachine`, active preset.
- Produces:

```java
public void queuePhoto(Minecraft minecraft);
public void tick(Minecraft minecraft, long nowNanos);
public void cancel(String reason);
public boolean hasQueuedShot();
public ShotReadiness readiness(long nowNanos);
public void noteSceneChanged(long nowNanos);
```

- [ ] **Step 1: Write failing orchestration tests around pure state transitions**

Use a small fake/adapter boundary where needed so the coordinator's decision logic can be tested without rendering. Required cases: immediate Photo (`timer=0`, settle Off), timer then settle, preflight blocked, cancel before capture, and capture listener failure returns readiness `FAILED`.

- [ ] **Step 2: Run RED**

Run targeted tests or `./gradlew test --no-daemon`; confirm missing coordinator fails compilation.

- [ ] **Step 3: Implement queue and preflight**

`queuePhoto()` resolves the active preset/native target dimensions, asks existing preflight for safety, blocks on `CANNOT_START`, starts `ShotPreparationMachine`, and keeps a pending immutable `CaptureRequest` so later config edits cannot mutate the queued shot.

- [ ] **Step 4: Implement tick and listener reactions**

When readiness reaches `READY`, call `captureManager.start(minecraft, pendingRequest)` once and transition to `CAPTURING`. On `onCompleted`, clear pending state and return to `READY`; on cancel/failure, retain a user-readable terminal readiness long enough for the viewfinder to display it.

- [ ] **Step 5: Wire coordinator into the client tick**

Register it in the listener hub and call `shotCoordinator.tick(client, System.nanoTime())` from `HyperShotClient.onEndTick`.

- [ ] **Step 6: Run GREEN and commit**

Run `./tools/run-core-tests.sh`, `./gradlew test --no-daemon`, then commit.

```bash
git add src/client/java/dev/hypershot/shot/ShotCoordinator.java src/client/java/dev/hypershot/HyperShotClient.java src/client/java/dev/hypershot/capture/CaptureManager.java src/test
git commit -m "feat: coordinate queued photo shots"
```

---

### Task 6: Replace immediate ScreenshotMixin capture with F2 gesture handling

**Files:**
- Create: `src/client/java/dev/hypershot/input/F2GestureController.java`
- Modify: `src/client/java/dev/hypershot/mixin/ScreenshotMixin.java`
- Modify: `src/client/java/dev/hypershot/HyperShotClient.java`

**Interfaces:**
- Consumes: `minecraft.options.keyScreenshot.isDown()`, config `F2Behavior`, `f2HoldThresholdMs`.
- Produces: `tick(Minecraft,long)`, dispatching either `shotCoordinator.queuePhoto()` or `HyperShotClient.openCameraViewfinder()`.

- [ ] **Step 1: Write a pure gesture-state test**

Model press at `0 ms`, release at `100 ms` => TAP; hold through `350 ms` => HOLD once; release after hold => no extra TAP. Put the state logic in a small nested/pure helper if necessary so it can be tested without GLFW.

- [ ] **Step 2: Run RED**

Run core/JUnit tests and confirm missing gesture controller.

- [ ] **Step 3: Implement gesture controller**

Use the configured Minecraft screenshot key mapping rather than hard-coding GLFW F2, so rebinding still works. In `VIEWFINDER_ONLY`, a press/short release opens the viewfinder; in `INSTANT_ONLY`, a release queues an instant Photo; default mode distinguishes tap vs hold.

- [ ] **Step 4: Change ScreenshotMixin responsibility**

When `replaceVanillaF2` is enabled, the mixin calls the vanilla screenshot callback with a concise `HyperShot controls F2`/queued message and cancels vanilla capture, but does **not** call `captureActivePreset()` itself. This prevents double-firing before gesture release.

- [ ] **Step 5: Wire and verify**

Tick the F2 controller before consuming HyperShot's custom capture key. Keep the custom HyperShot capture key as an explicit instant-shot shortcut.

Run `./gradlew clean test build --stacktrace --no-daemon`.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/hypershot/input/F2GestureController.java src/client/java/dev/hypershot/mixin/ScreenshotMixin.java src/client/java/dev/hypershot/HyperShotClient.java src/test
git commit -m "feat: add tap-hold screenshot key behavior"
```

---

### Task 7: Build non-pausing hybrid Photo viewfinder

**Files:**
- Create: `src/client/java/dev/hypershot/ui/CameraViewfinderScreen.java`
- Modify: `src/client/java/dev/hypershot/HyperShotClient.java`
- Modify: `src/client/java/dev/hypershot/ui/HyperShotTheme.java` only for reusable low-opacity overlay primitives.
- Modify: `src/client/resources/assets/hypershot/lang/en_us.json`

**Interfaces:**
- Consumes: `ShotCoordinator.readiness`, current preset, camera config, `GuideGeometry`.
- Produces: transparent/non-pausing screen with collapsed controls and a small expandable drawer.

- [ ] **Step 1: Add a source/compile guard for capture contamination**

`tools/verify-project.sh` must require `CameraViewfinderScreen` to check `HyperShotClient.captureManager().isRenderingCapturePass()` and skip **all** viewfinder/widget extraction during that pass. This requirement applies even when the active capture preset has `hideHud=false`.

- [ ] **Step 2: Run RED**

Run the verification script; it must fail before the screen exists.

- [ ] **Step 3: Implement collapsed viewfinder**

`CameraViewfinderScreen` overrides `isPauseScreen()` to return `false`. Keep the center visually quiet. Edge status displays `PHOTO`, resolution/format, and readiness. Bottom controls expose Timer, Settle, Guides, shutter, and drawer toggle. Do not copy the full Mod Menu settings page.

- [ ] **Step 4: Implement expanded drawer**

Drawer contains only shot-relevant controls: resolution shortcut, PNG/JPEG, timer, settle profile, guide type/opacity, appearance summary, and link to Camera Settings. Burst/Time/Cinematic may appear disabled with `Coming in next camera phase` only if they do not steal attention from Photo.

- [ ] **Step 5: Suppress the viewfinder during capture extraction**

Override the screen extraction path so that, while `CaptureManager.isRenderingCapturePass()` is true, it neither calls `super.extractRenderState(...)` nor draws guide/custom overlay state. Restore ordinary extraction immediately on the next normal frame.

- [ ] **Step 6: Render composition guides**

Use `GuideGeometry` and 1px fills/lines with opacity derived from `guideOpacity`. Do not modify world render state.

- [ ] **Step 7: Run build/startup smoke and commit**

Run:

```bash
./tools/verify-project.sh
./gradlew clean test build --stacktrace --no-daemon
```

Then run the existing controlled `runClient` smoke command from CI if available locally.

```bash
git add src/client/java/dev/hypershot/ui/CameraViewfinderScreen.java src/client/java/dev/hypershot/ui/HyperShotTheme.java src/client/java/dev/hypershot/HyperShotClient.java src/client/resources/assets/hypershot/lang/en_us.json tools/verify-project.sh
git commit -m "feat: add HyperShot photo viewfinder"
```

---

### Task 8: Connect Timer and Shader Settle to real camera movement

**Files:**
- Modify: `src/client/java/dev/hypershot/shot/ShotCoordinator.java`
- Create: `src/client/java/dev/hypershot/shot/CameraSceneSignature.java`
- Modify: `src/client/java/dev/hypershot/ui/CameraViewfinderScreen.java`

**Interfaces:**
- Consumes: player/camera position, yaw, pitch, current FOV; `ShaderSettleProfile`.
- Produces: deterministic movement detection that restarts settle wait while the user is still changing the composition.

- [ ] **Step 1: Write signature comparison tests**

A signature with unchanged position/yaw/pitch/FOV compares stable; a meaningful change over small epsilons compares changed. Do not use exact floating-point equality.

- [ ] **Step 2: Run RED**

Run tests and confirm missing signature type.

- [ ] **Step 3: Implement scene signature**

Snapshot camera position and orientation each tick while a queued shot is in `SETTLING_SHADERS`. If movement exceeds epsilon, call `noteSceneChanged(nowNanos)` and restart the settle deadline. Timer phase does not restart from camera movement.

- [ ] **Step 4: Expose remaining countdown**

`ShotReadiness.remainingNanos()` drives `3`, `2`, `1` countdown and `Shader settle: 0.8s` copy. Do not use wall-clock date/time.

- [ ] **Step 5: Run GREEN and commit**

Run core tests + Gradle build.

```bash
git add src/client/java/dev/hypershot/shot src/client/java/dev/hypershot/ui/CameraViewfinderScreen.java src/test
git commit -m "feat: settle shaders after camera composition"
```

---

### Task 9: Add focused Camera Settings and polish the existing SettingsScreen route

**Files:**
- Create: `src/client/java/dev/hypershot/ui/CameraSettingsScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/SettingsScreen.java`
- Modify: `src/client/resources/assets/hypershot/lang/en_us.json`

**Interfaces:**
- Consumes/mutates schema 3 camera fields only through `HyperShotConfig` + `HyperShotClient.saveConfig()`.
- Produces: understandable settings for F2 behavior, hold threshold, default timer, default guide, guide opacity, settle profile/custom duration, overlay fade, countdown sounds.

- [ ] **Step 1: Add compact/wide layout assertions where layout math is separable**

Ensure no row can produce negative/overlapping bounds at `420x240`, and normal `1280x720` keeps labels/control widths readable.

- [ ] **Step 2: Implement Camera Settings screen**

Use Minecraft buttons/edit boxes and plain language. The page must explicitly state `Shader Settle waits; it does not guarantee every shader converges correctly.`

- [ ] **Step 3: Route from SettingsScreen**

Replace any duplicate camera-detail controls with one clear `Camera controls`/`Camera behavior` entry. Do not clutter the main resolution screen.

- [ ] **Step 4: Run UI/core tests and build**

Run `./tools/run-core-tests.sh` and `./gradlew clean test build --stacktrace --no-daemon`.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/dev/hypershot/ui/CameraSettingsScreen.java src/client/java/dev/hypershot/ui/SettingsScreen.java src/client/resources/assets/hypershot/lang/en_us.json src/test
git commit -m "feat: polish camera settings"
```

---

### Task 10: Phase 1 regression guards, documentation, and clean verification

**Files:**
- Modify: `tools/verify-project.sh`
- Modify: `.github/workflows/build.yml` if the branch is not already included.
- Modify: `CHANGELOG.md`
- Modify: `docs/KNOWN_LIMITATIONS.md`
- Modify: `docs/TESTING.md` if present on branch.

**Interfaces:**
- Produces: clean automated proof that Phase 1 compiles/packages while keeping the final-render-target fix intact.

- [ ] **Step 1: Strengthen source guards**

Require:

```bash
! grep -R 'hypershot\$setMainRenderTarget' src
! grep -R '@Mutable.*mainRenderTarget' src
```

and assert the camera viewfinder capture-suppression guard and schema 3 fields are present.

- [ ] **Step 2: Run the complete dependency-free suite**

Run `./tools/run-core-tests.sh`.

Expected: all assertions pass; record the new count in verification output, not in design copy.

- [ ] **Step 3: Run clean Java 25 Gradle verification**

Run:

```bash
./gradlew clean test build --stacktrace --no-daemon
```

Expected: exit 0.

- [ ] **Step 4: Inspect production JAR**

Verify `fabric.mod.json`, Mod Menu entrypoint, `CameraViewfinderScreen.class`, `ShotCoordinator.class`, and existing `GameRendererTargetMixin.class`. Verify the forbidden render-target setter symbol is absent.

- [ ] **Step 5: Run Minecraft 26.2 startup smoke**

Use the same controlled Xvfb/runClient startup gate as existing CI. Startup is necessary but not sufficient to claim the camera capture path is fixed.

- [ ] **Step 6: Document real-hardware Phase 1 test sequence**

Required sequence:

1. Tap F2 Native Photo.
2. Hold F2 and open/close viewfinder.
3. Timer 3s + Rule of Thirds; verify guide absent from output.
4. Shader Settle Standard with shaders enabled; verify wait/reset behavior when camera moves.
5. 4K Photo from viewfinder.
6. 8K tiled Photo from viewfinder.
7. Cancel during timer and settle; confirm no capture starts.
8. Verify normal input/rendering resumes after every case.

- [ ] **Step 7: Update docs without overclaiming**

Changelog says `camera Phase 1 development`. Known limitations says Burst/Time/Cinematic are not implemented yet and shader settle is best-effort. Do not call the feature final until real capture testing passes.

- [ ] **Step 8: Commit**

```bash
git add tools/verify-project.sh .github/workflows/build.yml CHANGELOG.md docs/KNOWN_LIMITATIONS.md docs/TESTING.md
git commit -m "test: verify HyperShot camera phase 1"
```

---

## Plan Self-Review

- **Spec coverage:** Phase 1 covers the camera shell, Photo mode, F2 tap/hold, Timer, Guides, Shader Settle, ShotReadiness/queued-shot orchestration, capture contamination prevention, config migration, accessibility-friendly text states, and keeps `CaptureManager` per-image. Burst, Time, Cinematic, restoration, Gallery grouping, and Scene Snapshots are intentionally separate Phase 2-4 plans because they are independently reviewable subsystems.
- **Placeholder scan:** No `TBD`, `TODO`, `implement later`, or undefined implementation placeholders are allowed in this plan. Later phases are explicitly outside Phase 1 rather than placeholders inside it.
- **Type consistency:** `ShotPreparationMachine`, `ShotReadiness`, `ShotReadinessState`, `ShotCoordinator`, `F2GestureController`, `GuideGeometry`, and config field names are defined once above and reused consistently.
- **Release honesty:** Passing CI/startup is not enough. Phase 1 still requires actual Native/4K/8K capture testing and guide-contamination verification on real hardware before a final-release claim.
