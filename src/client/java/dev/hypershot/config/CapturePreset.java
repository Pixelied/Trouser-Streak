package dev.hypershot.config;

import dev.hypershot.core.OutputFormat;
import java.util.Objects;

public final class CapturePreset {
    public String id;
    public String name;
    public CaptureMode mode;
    public int width;
    public int height;
    public int tileSize;
    public int overlap;
    public int pngCompression;
    public OutputFormat outputFormat = OutputFormat.PNG;
    public float jpegQuality = 0.92f;
    public boolean hideHud;
    public boolean hideHand;
    public boolean hideBlockOutline;
    public boolean includeMetadata;

    public CapturePreset() {}

    public CapturePreset(String id, String name, CaptureMode mode, int width, int height, int tileSize,
                         int overlap, int pngCompression, boolean hideHud, boolean hideHand,
                         boolean hideBlockOutline, boolean includeMetadata) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.mode = Objects.requireNonNull(mode);
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.overlap = overlap;
        this.pngCompression = pngCompression;
        this.hideHud = hideHud;
        this.hideHand = hideHand;
        this.hideBlockOutline = hideBlockOutline;
        this.includeMetadata = includeMetadata;
        this.outputFormat = OutputFormat.PNG;
        this.jpegQuality = 0.92f;
        validate();
    }

    public CapturePreset(String id, String name, CaptureMode mode, int width, int height, int tileSize,
                         int overlap, int pngCompression, OutputFormat outputFormat, float jpegQuality,
                         boolean hideHud, boolean hideHand, boolean hideBlockOutline, boolean includeMetadata) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.mode = Objects.requireNonNull(mode);
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.overlap = overlap;
        this.pngCompression = pngCompression;
        this.outputFormat = Objects.requireNonNull(outputFormat);
        this.jpegQuality = jpegQuality;
        this.hideHud = hideHud;
        this.hideHand = hideHand;
        this.hideBlockOutline = hideBlockOutline;
        this.includeMetadata = includeMetadata;
        validate();
    }

    public void validate() {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mode, "mode");
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative preset dimensions");
        if (tileSize < 256 || tileSize > 16384) throw new IllegalArgumentException("Tile size outside 256..16384");
        if (overlap < 0 || overlap >= tileSize / 2) throw new IllegalArgumentException("Invalid tile overlap");
        if (pngCompression < 0 || pngCompression > 9) throw new IllegalArgumentException("PNG compression outside 0..9");
        if (outputFormat == null) outputFormat = OutputFormat.PNG;
        if (!Float.isFinite(jpegQuality) || jpegQuality < 0.01f || jpegQuality > 1.0f) jpegQuality = 0.92f;
    }

    public CapturePreset copy(String newId, String newName) {
        return new CapturePreset(newId, newName, mode, width, height, tileSize, overlap, pngCompression, outputFormat, jpegQuality,
                hideHud, hideHand, hideBlockOutline, includeMetadata);
    }
}
