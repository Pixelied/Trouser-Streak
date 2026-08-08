package dev.hypershot.core;

import java.time.Duration;

/** Thread-safe measured progress with an exponentially weighted recent throughput estimate. */
public final class CaptureProgress {
    private final int totalTiles;
    private final long totalPixels;
    private CapturePhase phase = CapturePhase.QUEUED;
    private int tilesCompleted;
    private long pixelsCompleted;
    private long startedNanos;
    private long lastNanos;
    private long lastPixels;
    private double pixelsPerSecond;

    public CaptureProgress(int totalTiles, long totalPixels) {
        if (totalTiles <= 0 || totalPixels <= 0) throw new IllegalArgumentException("progress totals");
        this.totalTiles = totalTiles;
        this.totalPixels = totalPixels;
    }

    public synchronized void begin(CapturePhase phase, long nowNanos) {
        this.phase = phase;
        if (startedNanos == 0) startedNanos = nowNanos;
        lastNanos = nowNanos;
        lastPixels = pixelsCompleted;
    }

    public synchronized void setPhase(CapturePhase phase) { this.phase = phase; }

    public synchronized void tileCompleted(long pixels, long nowNanos) {
        if (pixels < 0) throw new IllegalArgumentException("pixels");
        tilesCompleted = Math.min(totalTiles, tilesCompleted + 1);
        pixelsCompleted = Math.min(totalPixels, CheckedMath.add(pixelsCompleted, pixels));
        long nanos = nowNanos - lastNanos;
        long deltaPixels = pixelsCompleted - lastPixels;
        if (nanos > 0 && deltaPixels > 0) {
            double instant = deltaPixels * 1_000_000_000.0 / nanos;
            pixelsPerSecond = pixelsPerSecond == 0 ? instant : pixelsPerSecond * 0.65 + instant * 0.35;
        }
        lastNanos = nowNanos;
        lastPixels = pixelsCompleted;
    }

    public synchronized CaptureProgressSnapshot snapshot(long nowNanos) {
        long elapsedNanos = startedNanos == 0 ? 0 : Math.max(0, nowNanos - startedNanos);
        long remaining = Math.max(0, totalPixels - pixelsCompleted);
        long etaNanos = pixelsPerSecond <= 0 ? 0 : Math.max(0, (long) (remaining / pixelsPerSecond * 1_000_000_000.0));
        return new CaptureProgressSnapshot(phase, tilesCompleted, totalTiles, pixelsCompleted, totalPixels,
                pixelsCompleted / (double) totalPixels, Duration.ofNanos(elapsedNanos), Duration.ofNanos(etaNanos), pixelsPerSecond);
    }
}
