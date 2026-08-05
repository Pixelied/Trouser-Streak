package dev.hypershot.core;

public final class CaptureEstimator {
    private static final long MIB = 1024L * 1024L;
    private CaptureEstimator() {}

    public static CaptureEstimate estimate(CaptureSpec spec) {
        TileLayout layout = TileLayout.create(spec.width(), spec.height(), spec.tileSize(), spec.overlap());
        long outputPixels = layout.totalPixels();
        long renderedPixels = 0;
        long maxTileBytes = 0;
        for (Tile tile : layout.tiles()) {
            long pixels = CheckedMath.multiply(tile.renderWidth(), tile.renderHeight());
            renderedPixels = CheckedMath.add(renderedPixels, pixels);
            maxTileBytes = Math.max(maxTileBytes, CheckedMath.multiply(pixels, 4));
        }
        renderedPixels = CheckedMath.multiply(renderedPixels, spec.temporalSamples());
        // Two tile buffers, one encoder row group, bounded queue and fixed overhead.
        long rowGroup = CheckedMath.multiply(spec.width(), 4L * 16L);
        long peakHeap = CheckedMath.add(CheckedMath.multiply(maxTileBytes, 2), CheckedMath.add(rowGroup, 48L * MIB));
        // Disk spool stores one final RGBA copy plus manifest/metadata slack.
        long spool = CheckedMath.bytesForPixels(spec.width(), spec.height(), 4);
        long temporary = CheckedMath.add(spool, Math.max(64L * MIB, spool / 20));
        long finalBytes = switch (spec.format()) {
            case PNG -> Math.max(1024, spool / 2);
            case JPEG -> Math.max(1024, outputPixels);
        };
        return new CaptureEstimate(outputPixels, renderedPixels, peakHeap, temporary, finalBytes);
    }
}
