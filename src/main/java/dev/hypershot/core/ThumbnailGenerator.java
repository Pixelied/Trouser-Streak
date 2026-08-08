package dev.hypershot.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Generates a real, bounded-memory preview by sampling the disk-backed capture surface. */
public final class ThumbnailGenerator {
    private ThumbnailGenerator() {}

    public static Resolution fit(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("thumbnail dimensions");
        }
        double scale = Math.min(1.0, Math.min((double) maxWidth / sourceWidth, (double) maxHeight / sourceHeight));
        return new Resolution(Math.max(1, (int) Math.round(sourceWidth * scale)),
                Math.max(1, (int) Math.round(sourceHeight * scale)));
    }

    public static void writePng(DiskBackedRgbaSurface surface, int sourceWidth, int sourceHeight,
                                Path output, int maxWidth, int maxHeight, int compression) throws IOException {
        Objects.requireNonNull(surface, "surface");
        Resolution size = fit(sourceWidth, sourceHeight, maxWidth, maxHeight);
        byte[] sourceRow = new byte[CheckedMath.checkedInt(CheckedMath.multiply(sourceWidth, 4), "thumbnail source row")];
        byte[] outputRow = new byte[CheckedMath.checkedInt(CheckedMath.multiply(size.width(), 4), "thumbnail output row")];
        int lastSourceY = -1;
        try (StreamingPngWriter writer = new StreamingPngWriter(output, size.width(), size.height(), compression)) {
            for (int y = 0; y < size.height(); y++) {
                int sourceY = sampleCoordinate(y, size.height(), sourceHeight);
                if (sourceY != lastSourceY) {
                    surface.readRow(sourceY, sourceRow);
                    lastSourceY = sourceY;
                }
                for (int x = 0; x < size.width(); x++) {
                    int sourceX = sampleCoordinate(x, size.width(), sourceWidth);
                    System.arraycopy(sourceRow, sourceX * 4, outputRow, x * 4, 4);
                }
                writer.writeRow(outputRow);
            }
        }
    }

    private static int sampleCoordinate(int outputCoordinate, int outputSize, int sourceSize) {
        long numerator = (2L * outputCoordinate + 1L) * sourceSize;
        int coordinate = (int) (numerator / (2L * outputSize));
        return Math.min(sourceSize - 1, coordinate);
    }
}
