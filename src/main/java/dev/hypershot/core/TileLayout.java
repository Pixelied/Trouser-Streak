package dev.hypershot.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TileLayout(int width, int height, int tileSize, int overlap, int columns, int rows, List<Tile> tiles) {
    public TileLayout {
        tiles = Collections.unmodifiableList(new ArrayList<>(tiles));
    }

    public static TileLayout create(int width, int height, int tileSize, int overlap) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Output dimensions must be positive");
        if (tileSize < 64) throw new IllegalArgumentException("Tile size must be at least 64");
        if (overlap < 0 || overlap >= tileSize / 2) throw new IllegalArgumentException("Overlap must be non-negative and smaller than half a tile");
        int columns = ceilDiv(width, tileSize);
        int rows = ceilDiv(height, tileSize);
        long count = CheckedMath.multiply(columns, rows);
        if (count > 1_000_000L) throw new IllegalArgumentException("Tile count is unreasonable: " + count);
        List<Tile> tiles = new ArrayList<>((int) count);
        int index = 0;
        for (int row = 0; row < rows; row++) {
            int y = row * tileSize;
            int contentHeight = Math.min(tileSize, height - y);
            for (int column = 0; column < columns; column++) {
                int x = column * tileSize;
                int contentWidth = Math.min(tileSize, width - x);
                int renderX = Math.max(0, x - overlap);
                int renderY = Math.max(0, y - overlap);
                int renderRight = Math.min(width, x + contentWidth + overlap);
                int renderBottom = Math.min(height, y + contentHeight + overlap);
                tiles.add(new Tile(index++, column, row, x, y, contentWidth, contentHeight,
                        renderX, renderY, renderRight - renderX, renderBottom - renderY,
                        x - renderX, y - renderY));
            }
        }
        return new TileLayout(width, height, tileSize, overlap, columns, rows, tiles);
    }

    public long totalPixels() { return CheckedMath.multiply(width, height); }

    private static int ceilDiv(int value, int divisor) {
        return (int) ((value + (long) divisor - 1L) / divisor);
    }
}
