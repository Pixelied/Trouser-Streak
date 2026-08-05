#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
VERSION=9.5.1
SHA256=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/manual/gradle-$VERSION"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
DIST="$CACHE/gradle-$VERSION"
mkdir -p "$CACHE"
if [ ! -x "$DIST/bin/gradle" ]; then
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 "$URL" -o "$ZIP"; else wget -O "$ZIP" "$URL"; fi
  ACTUAL=$(sha256sum "$ZIP" 2>/dev/null | awk '{print $1}' || shasum -a 256 "$ZIP" | awk '{print $1}')
  [ "$ACTUAL" = "$SHA256" ] || { echo "Gradle checksum mismatch" >&2; exit 2; }
  rm -rf "$DIST" "$CACHE/gradle-$VERSION.tmp"
  mkdir -p "$CACHE/gradle-$VERSION.tmp"
  unzip -q "$ZIP" -d "$CACHE/gradle-$VERSION.tmp"
  mv "$CACHE/gradle-$VERSION.tmp/gradle-$VERSION" "$DIST"
  rm -rf "$CACHE/gradle-$VERSION.tmp"
fi
exec "$DIST/bin/gradle" "$@"
