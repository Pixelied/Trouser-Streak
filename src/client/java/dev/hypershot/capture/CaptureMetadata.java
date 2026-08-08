package dev.hypershot.capture;

import java.time.OffsetDateTime;
import java.util.List;

public record CaptureMetadata(
        int schemaVersion,
        String captureId,
        OffsetDateTime capturedAt,
        String filename,
        int width,
        int height,
        String format,
        long fileSize,
        long durationMillis,
        int tileWidth,
        int tileHeight,
        int tileCount,
        String captureMode,
        String preset,
        String minecraftVersion,
        String javaVersion,
        String operatingSystem,
        String renderBackend,
        String gpu,
        String driver,
        String world,
        String server,
        String dimension,
        Double playerX,
        Double playerY,
        Double playerZ,
        Float yaw,
        Float pitch,
        Float fov,
        boolean hudHidden,
        boolean handHidden,
        boolean blockOutlineHidden,
        List<String> activeResourcePacks) {
}
