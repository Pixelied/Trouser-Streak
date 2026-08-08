package dev.hypershot.core.camera;

import dev.hypershot.core.CaptureSafetyState;

import java.util.Objects;

public record SequencePreflight(SequenceEstimate estimate, long requiredDiskBytes, long availableDiskBytes,
                                CaptureSafetyState safetyState, String reason) {
    public SequencePreflight {
        Objects.requireNonNull(estimate);
        Objects.requireNonNull(safetyState);
        Objects.requireNonNull(reason);
        if (requiredDiskBytes < 0 || availableDiskBytes < 0) throw new IllegalArgumentException("Invalid sequence disk budget");
    }
}
