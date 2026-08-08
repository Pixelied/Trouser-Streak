package dev.hypershot.capture;

import dev.hypershot.config.CaptureMode;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.Resolution;
import dev.hypershot.core.OutputFormat;

import java.util.Objects;

public record CaptureRequest(String presetId, String presetName, CaptureMode mode, Resolution resolution,
                             int tileSize, int overlap, int pngCompression, OutputFormat outputFormat, float jpegQuality, boolean hideHud,
                             boolean hideHand, boolean hideBlockOutline, boolean includeMetadata) {
    public CaptureRequest {
        Objects.requireNonNull(presetId);
        Objects.requireNonNull(presetName);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(resolution);
        Objects.requireNonNull(outputFormat);
        if (tileSize < 256 || overlap < 0 || overlap >= tileSize / 2) throw new IllegalArgumentException("Invalid tile geometry");
        if (pngCompression < 0 || pngCompression > 9) throw new IllegalArgumentException("PNG compression");
        if (!Float.isFinite(jpegQuality) || jpegQuality < 0.01f || jpegQuality > 1.0f) throw new IllegalArgumentException("JPEG quality");
    }

    public static CaptureRequest from(CapturePreset preset, int nativeWidth, int nativeHeight) {
        int width = preset.width == 0 ? nativeWidth : preset.width;
        int height = preset.height == 0 ? nativeHeight : preset.height;
        return new CaptureRequest(preset.id, preset.name, preset.mode, new Resolution(width, height),
                preset.tileSize, preset.overlap, preset.pngCompression, preset.outputFormat, preset.jpegQuality, preset.hideHud,
                preset.hideHand, preset.hideBlockOutline, preset.includeMetadata);
    }
}
