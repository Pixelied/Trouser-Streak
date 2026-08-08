# Dependency licenses

HyperShot does not bundle third-party codecs or native libraries in this release.
Runtime dependencies are supplied by the Minecraft/Fabric installation:

- Fabric Loader — Apache License 2.0
- Fabric API — Apache License 2.0
- Mod Menu — MIT License (optional runtime integration)
- Gson — Apache License 2.0 (provided by Minecraft)
- JOML — BSD 2-Clause (provided by Minecraft)

Minecraft itself is not redistributed by this project.

The small wrapper bootstrap in `tools/wrapper-src` is original HyperShot project code under the repository MIT license. It downloads the unmodified Gradle 9.5.1 binary distribution and verifies the checksum declared in `gradle-wrapper.properties`; Gradle itself is not bundled.
