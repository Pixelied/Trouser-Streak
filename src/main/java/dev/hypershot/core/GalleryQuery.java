package dev.hypershot.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record GalleryQuery(String text, Boolean favorite, Integer minimumWidth, String captureMode) {
    public static List<GalleryEntry> apply(List<GalleryEntry> source, GalleryQuery query) {
        String needle = query.text == null ? "" : query.text.toLowerCase(Locale.ROOT).trim();
        return source.stream().filter(entry -> {
            boolean textMatch = needle.isEmpty() || entry.filename().toLowerCase(Locale.ROOT).contains(needle) ||
                    entry.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
            boolean favoriteMatch = query.favorite == null || entry.favorite() == query.favorite;
            boolean widthMatch = query.minimumWidth == null || entry.width() >= query.minimumWidth;
            boolean modeMatch = query.captureMode == null || entry.captureMode().equalsIgnoreCase(query.captureMode);
            return textMatch && favoriteMatch && widthMatch && modeMatch;
        }).toList();
    }

    public static List<GalleryEntry> sort(List<GalleryEntry> source, GallerySort sort) {
        Comparator<GalleryEntry> comparator = switch (sort) {
            case NAME_ASC -> Comparator.comparing(GalleryEntry::filename, String.CASE_INSENSITIVE_ORDER);
            case FILE_SIZE_ASC -> Comparator.comparingLong(GalleryEntry::fileSize);
            case FILE_SIZE_DESC -> Comparator.comparingLong(GalleryEntry::fileSize).reversed();
            case RESOLUTION_DESC -> Comparator.comparingLong((GalleryEntry e) -> CheckedMath.multiply(e.width(), e.height())).reversed();
        };
        ArrayList<GalleryEntry> copy = new ArrayList<>(source);
        copy.sort(comparator.thenComparing(GalleryEntry::id));
        return List.copyOf(copy);
    }
}
