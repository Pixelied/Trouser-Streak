# Troubleshooting

## Java or Gradle version errors

Use a Java 25 JDK. Verify with `java -version`. The wrapper pins Gradle 9.5.1 and verifies the distribution checksum.

## Capture refuses to start

HyperShot validates free disk space, image dimensions, format limits, and encoder heap requirements before changing renderer state. Open Diagnostics from Settings and inspect the output directory, maximum texture size, heap, and codec selection.

## Tiled capture has shader seams

Increase overlap, disable the shader pack's screen-space effects, or use native capture. Some temporal history buffers, auto exposure, bloom, SSAO, and reflections cannot be made tile-consistent without shader-specific integration.

## Gallery image is marked missing

The file was moved or deleted outside HyperShot. Reveal the capture folder or remove the stale entry after restoring the file.

## First launch cannot download Gradle

Download Gradle 9.5.1 on a networked machine or install it locally, then run `gradle wrapper --gradle-version 9.5.1` and build again.

## Report a failure

Open HyperShot Settings → Diagnostics → Export. Privacy presets omit world/server/coordinate fields from capture metadata; review diagnostic files before sharing them.
