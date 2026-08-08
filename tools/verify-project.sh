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
grep -q 'CURRENT_SCHEMA = 4' "$CONFIG" || { echo 'Camera config must use schema 4.' >&2; exit 1; }
grep -q 'F2Behavior f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER' "$CONFIG" || { echo 'Default F2 camera behavior missing.' >&2; exit 1; }
grep -q 'CameraMode cameraMode = CameraMode.PHOTO' "$CONFIG" || { echo 'Default camera mode missing.' >&2; exit 1; }
grep -q 'GuideType guideType = GuideType.RULE_OF_THIRDS' "$CONFIG" || { echo 'Default composition guide missing.' >&2; exit 1; }
grep -q 'ShaderSettleProfile shaderSettleProfile = ShaderSettleProfile.STANDARD' "$CONFIG" || { echo 'Default shader settle profile missing.' >&2; exit 1; }
HUB=src/client/java/dev/hypershot/capture/CaptureListenerHub.java
CLIENT=src/client/java/dev/hypershot/HyperShotClient.java
test -f "$HUB" || { echo 'CaptureListenerHub is required.' >&2; exit 1; }
grep -q 'listenerHub.add(notifications)' "$CLIENT" || { echo 'Notifications must attach through CaptureListenerHub.' >&2; exit 1; }
COORD=src/client/java/dev/hypershot/shot/ShotCoordinator.java
test -f "$COORD" || { echo 'ShotCoordinator is required.' >&2; exit 1; }
grep -q 'void queuePhoto' "$COORD" || { echo 'ShotCoordinator must queue Photo shots.' >&2; exit 1; }
grep -q 'ShotPreparationMachine' "$COORD" || { echo 'ShotCoordinator must use the pure preparation state machine.' >&2; exit 1; }
rm -rf /tmp/hypershot-wrapper-classes
mkdir -p /tmp/hypershot-wrapper-classes
javac --release 17 -d /tmp/hypershot-wrapper-classes tools/wrapper-src/org/gradle/wrapper/GradleWrapperMain.java
jar --create --file /tmp/hypershot-wrapper-test.jar -C /tmp/hypershot-wrapper-classes .
jar tf /tmp/hypershot-wrapper-test.jar | grep -q 'org/gradle/wrapper/GradleWrapperMain.class'
echo "HyperShot local verification: PASS"
