package dev.hypershot.core;

import java.util.List;

public record GalleryEntry(String id, String filename, int width, int height, long fileSize,
                           boolean favorite, List<String> tags, String captureMode) {
    public GalleryEntry { tags = List.copyOf(tags); }
}
