package dev.hypershot.gallery;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CaptureRecord {
    public String id;
    public String imagePath;
    public String metadataPath;
    public String thumbnailPath;
    public String filename;
    public long timestampEpochMillis;
    public int width;
    public int height;
    public String format;
    public long fileSize;
    public String preset;
    public String captureMode;
    public boolean favorite;
    public int rating;
    public List<String> tags = new ArrayList<>();
    public boolean missing;

    /** Optional camera-sequence grouping. Null groupId means an ordinary single Photo. */
    public String groupId;
    public String groupType;
    /** One-based frame index within the group. */
    public int groupIndex;
    public int groupCount;
    public String groupLabel;
    /** Nullable for backward compatibility: old records are treated as complete. */
    public Boolean groupComplete;

    public CaptureRecord() {}

    public CaptureRecord(String id, Path image, Path metadata, Path thumbnail, long timestampEpochMillis,
                         int width, int height, String format, long fileSize, String preset, String captureMode) {
        this.id = Objects.requireNonNull(id);
        this.imagePath = image.toAbsolutePath().normalize().toString();
        this.metadataPath = metadata == null ? null : metadata.toAbsolutePath().normalize().toString();
        this.thumbnailPath = thumbnail == null ? null : thumbnail.toAbsolutePath().normalize().toString();
        this.filename = image.getFileName().toString();
        this.timestampEpochMillis = timestampEpochMillis;
        this.width = width;
        this.height = height;
        this.format = Objects.requireNonNullElse(format, "PNG");
        this.fileSize = fileSize;
        this.preset = Objects.requireNonNullElse(preset, "Unknown");
        this.captureMode = Objects.requireNonNullElse(captureMode, "UNKNOWN");
    }

    public Path image() { return Path.of(imagePath); }
    public Path metadata() { return metadataPath == null ? null : Path.of(metadataPath); }
    public Path thumbnail() { return thumbnailPath == null ? null : Path.of(thumbnailPath); }
    public Instant timestamp() { return Instant.ofEpochMilli(timestampEpochMillis); }
    public double aspectRatio() { return height == 0 ? 0.0 : (double) width / height; }
    public boolean belongsToGroup() { return groupId != null && !groupId.isBlank(); }
    public boolean isGroupComplete() { return groupComplete == null || groupComplete; }
}
