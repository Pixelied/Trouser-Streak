package dev.hypershot.core;

public final class CapturePreflightCalculator {
    private static final long HIGH_LOAD_PIXELS = 500_000_000L;
    private static final long LIKELY_SAFE_PIXELS = 100_000_000L;

    private CapturePreflightCalculator() {}

    public static CapturePreflight calculate(CaptureSpec spec, long freeDiskBytes, long freeHeapBytes, long diskReserveBytes) {
        if (freeDiskBytes < 0 || freeHeapBytes < 0 || diskReserveBytes < 0) {
            throw new IllegalArgumentException("Negative resource budget");
        }
        CaptureEstimate estimate = CaptureEstimator.estimate(spec);
        TileLayout layout = TileLayout.create(spec.width(), spec.height(), spec.tileSize(), spec.overlap());
        long raw = CheckedMath.bytesForPixels(spec.width(), spec.height(), 4);
        long availableDisk = Math.max(0L, freeDiskBytes - diskReserveBytes);
        long requiredDisk = CheckedMath.add(estimate.temporaryBytes(), estimate.estimatedFinalBytes());

        CaptureSafetyState state;
        String reason;
        if (requiredDisk > availableDisk) {
            state = CaptureSafetyState.CANNOT_START;
            reason = "Not enough free disk space after the safety reserve";
        } else if (estimate.peakHeapBytes() > freeHeapBytes) {
            state = CaptureSafetyState.CANNOT_START;
            reason = "Estimated peak Java memory exceeds currently free heap";
        } else if (requiredDisk > fraction(availableDisk, 80) || estimate.peakHeapBytes() > fraction(freeHeapBytes, 80)) {
            state = CaptureSafetyState.NOT_RECOMMENDED;
            reason = "Capture is too close to current memory or disk limits";
        } else if (estimate.outputPixels() >= HIGH_LOAD_PIXELS || layout.tiles().size() >= 100) {
            state = CaptureSafetyState.HIGH_LOAD;
            reason = "Extreme capture: expect heavy GPU, CPU, and disk usage";
        } else if (estimate.outputPixels() >= LIKELY_SAFE_PIXELS || requiredDisk > fraction(availableDisk, 40)) {
            state = CaptureSafetyState.LIKELY_SAFE;
            reason = "Large capture with comfortable resource margins";
        } else {
            state = CaptureSafetyState.SAFE;
            reason = "Resource estimates are comfortably within current limits";
        }

        return new CapturePreflight(estimate, layout.tiles().size(), raw, freeDiskBytes, diskReserveBytes,
                availableDisk, freeHeapBytes, state, reason);
    }

    private static long fraction(long value, int percent) {
        if (value <= 0) return 0;
        return (value / 100L) * percent + ((value % 100L) * percent) / 100L;
    }
}
