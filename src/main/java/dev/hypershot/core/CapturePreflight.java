package dev.hypershot.core;

import java.util.Objects;

public record CapturePreflight(
        CaptureEstimate estimate,
        int tileCount,
        long rawRgbaBytes,
        long freeDiskBytes,
        long diskReserveBytes,
        long availableDiskBytes,
        long freeHeapBytes,
        CaptureSafetyState safetyState,
        String reason
) {
    public CapturePreflight {
        Objects.requireNonNull(estimate);
        Objects.requireNonNull(safetyState);
        Objects.requireNonNull(reason);
        if (tileCount <= 0 || rawRgbaBytes <= 0 || freeDiskBytes < 0 || diskReserveBytes < 0 ||
                availableDiskBytes < 0 || freeHeapBytes < 0) {
            throw new IllegalArgumentException("Invalid capture preflight");
        }
    }

    public long requiredDiskBytes() {
        return CheckedMath.add(estimate.temporaryBytes(), estimate.estimatedFinalBytes());
    }
}
