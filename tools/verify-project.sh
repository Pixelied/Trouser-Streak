#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
git diff --check
./tools/run-core-tests.sh
if grep -R --line-number -E 'hypershot\$setMainRenderTarget|@Mutable.*mainRenderTarget|mainRenderTarget.*@Mutable' src/client/java/dev/hypershot; then
  echo "Illegal mutable main render target accessor is forbidden." >&2
  exit 1
fi
CONFIG=src/client/java/dev/hypershot/config/HyperShotConfig.java
grep -q 'CURRENT_SCHEMA = 6' "$CONFIG" || { echo 'Cinematic camera config must use schema 6.' >&2; exit 1; }
grep -q 'F2Behavior f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER' "$CONFIG" || { echo 'Default F2 camera behavior missing.' >&2; exit 1; }
grep -q 'CameraMode cameraMode = CameraMode.PHOTO' "$CONFIG" || { echo 'Default camera mode missing.' >&2; exit 1; }
grep -q 'GuideType guideType = GuideType.RULE_OF_THIRDS' "$CONFIG" || { echo 'Default composition guide missing.' >&2; exit 1; }
grep -q 'ShaderSettleProfile shaderSettleProfile = ShaderSettleProfile.STANDARD' "$CONFIG" || { echo 'Default shader settle profile missing.' >&2; exit 1; }
grep -q 'int burstFrameCount = 5' "$CONFIG" || { echo 'Default burst count missing.' >&2; exit 1; }
grep -q 'long burstIntervalMs = 250' "$CONFIG" || { echo 'Default burst interval missing.' >&2; exit 1; }
grep -q 'boolean cinematicWorldFreeze = false' "$CONFIG" || { echo 'Cinematic freeze must default off until hardware validation.' >&2; exit 1; }
grep -q 'boolean cinematicCameraLock = true' "$CONFIG" || { echo 'Cinematic camera lock default missing.' >&2; exit 1; }
grep -q 'boolean cinematicFovLock = true' "$CONFIG" || { echo 'Cinematic FOV lock default missing.' >&2; exit 1; }
grep -q 'boolean cinematicCleanFrame = true' "$CONFIG" || { echo 'Cinematic Clean Frame default missing.' >&2; exit 1; }

HUB=src/client/java/dev/hypershot/capture/CaptureListenerHub.java
CLIENT=src/client/java/dev/hypershot/HyperShotClient.java
test -f "$HUB" || { echo 'CaptureListenerHub is required.' >&2; exit 1; }
grep -q 'listenerHub.add(notifications)' "$CLIENT" || { echo 'Notifications must attach through CaptureListenerHub.' >&2; exit 1; }
COORD=src/client/java/dev/hypershot/shot/ShotCoordinator.java
test -f "$COORD" || { echo 'ShotCoordinator is required.' >&2; exit 1; }
grep -q 'void queuePhoto' "$COORD" || { echo 'ShotCoordinator must queue Photo shots.' >&2; exit 1; }
grep -q 'void queueBurst' "$COORD" || { echo 'ShotCoordinator must queue Burst sessions.' >&2; exit 1; }
grep -q 'void queueTimeBracket' "$COORD" || { echo 'ShotCoordinator must queue Time sessions.' >&2; exit 1; }
grep -q 'ShotPreparationMachine' "$COORD" || { echo 'ShotCoordinator must use the pure preparation state machine.' >&2; exit 1; }
grep -q 'PreparationContinuation.next' "$COORD" || { echo 'ShotCoordinator must distinguish initial Time preparation from post-lighting settle.' >&2; exit 1; }

OVERLAY=src/client/java/dev/hypershot/ui/CameraViewfinderOverlay.java
CONTROLLER=src/client/java/dev/hypershot/input/F2GestureController.java
MIXIN=src/client/java/dev/hypershot/mixin/ScreenshotMixin.java
test -f "$OVERLAY" || { echo 'Camera viewfinder overlay is required.' >&2; exit 1; }
test -f "$CONTROLLER" || { echo 'F2 gesture controller is required.' >&2; exit 1; }
grep -q 'isRenderingCapturePass()' "$OVERLAY" || { echo 'Camera overlay must suppress itself during capture.' >&2; exit 1; }
if grep -q 'captureActivePreset' "$MIXIN"; then
  echo 'ScreenshotMixin must not directly start a HyperShot capture.' >&2
  exit 1
fi

for required in \
  src/client/java/dev/hypershot/shot/CinematicSceneController.java \
  src/client/java/dev/hypershot/shot/CameraLockController.java \
  src/client/java/dev/hypershot/shot/ChunkReadinessProbe.java \
  src/client/java/dev/hypershot/mixin/CameraFovMixin.java \
  src/main/java/dev/hypershot/core/camera/CinematicOptions.java \
  src/main/java/dev/hypershot/core/camera/CinematicCapabilities.java \
  src/main/java/dev/hypershot/core/camera/SceneRestoreState.java; do
  test -f "$required" || { echo "Missing cinematic component: $required" >&2; exit 1; }
done
grep -q 'CameraFovMixin' src/client/resources/hypershot.client.mixins.json || { echo 'Cinematic FOV mixin not registered.' >&2; exit 1; }
FOV_MIXIN=src/client/java/dev/hypershot/mixin/CameraFovMixin.java
grep -q 'cameraLockController()' "$FOV_MIXIN" || { echo 'FOV lock must resolve the scoped camera lock controller.' >&2; exit 1; }
grep -q 'lockedFov()' "$FOV_MIXIN" || { echo 'FOV lock must return the scoped locked FOV value.' >&2; exit 1; }

rm -rf /tmp/hypershot-wrapper-classes
mkdir -p /tmp/hypershot-wrapper-classes
javac --release 17 -d /tmp/hypershot-wrapper-classes tools/wrapper-src/org/gradle/wrapper/GradleWrapperMain.java
jar --create --file /tmp/hypershot-wrapper-test.jar -C /tmp/hypershot-wrapper-classes .
jar tf /tmp/hypershot-wrapper-test.jar | grep -q 'org/gradle/wrapper/GradleWrapperMain.class'
echo "HyperShot local verification: PASS"
