#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
git diff --check
./tools/run-core-tests.sh
if grep -R --line-number -E 'hypershot\$setMainRenderTarget|@Mutable.*mainRenderTarget|mainRenderTarget.*@Mutable' src/client/java/dev/hypershot; then
  echo "Illegal mutable main render target accessor is forbidden." >&2
  exit 1
fi
rm -rf /tmp/hypershot-wrapper-classes
mkdir -p /tmp/hypershot-wrapper-classes
javac --release 17 -d /tmp/hypershot-wrapper-classes tools/wrapper-src/org/gradle/wrapper/GradleWrapperMain.java
jar --create --file /tmp/hypershot-wrapper-test.jar -C /tmp/hypershot-wrapper-classes .
jar tf /tmp/hypershot-wrapper-test.jar | grep -q 'org/gradle/wrapper/GradleWrapperMain.class'
echo "HyperShot local verification: PASS"
