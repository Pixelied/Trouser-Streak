package dev.hypershot.core;

public record Tile(
        int index,
        int column,
        int row,
        int contentX,
        int contentY,
        int contentWidth,
        int contentHeight,
        int renderX,
        int renderY,
        int renderWidth,
        int renderHeight,
        int cropLeft,
        int cropTop) {
    public Tile {
        if (index < 0 || column < 0 || row < 0 || contentX < 0 || contentY < 0 ||
                contentWidth <= 0 || contentHeight <= 0 || renderX < 0 || renderY < 0 ||
                renderWidth <= 0 || renderHeight <= 0 || cropLeft < 0 || cropTop < 0) {
            throw new IllegalArgumentException("Invalid tile geometry");
        }
    }
}
