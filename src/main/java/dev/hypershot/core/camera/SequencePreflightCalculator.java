package dev.hypershot.core.camera;

import dev.hypershot.core.CapturePreflight;
import dev.hypershot.core.CaptureSafetyState;
import dev.hypershot.core.CheckedMath;

public final class SequencePreflightCalculator {
    private static final long HIGH_LOAD_TOTAL_PIXELS = 500_000_000L;

    private SequencePreflightCalculator() {}

    public static SequencePreflight calculate(CapturePreflight perFrame, int frameCount) {
        if (perFrame == null) throw new IllegalArgumentException("Per-frame preflight is required");
        SequenceEstimate sequence = SequenceEstimateCalculator.multiply(perFrame.estimate(), frameCount);
        long requiredDisk = CheckedMath.add(sequence.worstTemporaryBytes(), sequence.totalFinalBytes());
        long availableDisk = perFrame.availableDiskBytes();
        CaptureSafetyState state;
        String reason;
        if (requiredDisk > availableDisk) {
            state = CaptureSafetyState.CANNOT_START;
            reason = "The full sequence would exceed free disk space after the safety reserve";
        } else if (sequence.peakHeapBytes() > perFrame.freeHeapBytes()) {
            state = CaptureSafetyState.CANNOT_START;
            reason = "A sequence frame exceeds currently free Java heap";
        } else if (requiredDisk > fraction(availableDisk, 80)) {
            state = CaptureSafetyState.NOT_RECOMMENDED;
            reason = "The full sequence is too close to the current disk limit";
        } else if (sequence.totalOutputPixels() >= HIGH_LOAD_TOTAL_PIXELS || frameCount >= 10) {
            state = CaptureSafetyState.HIGH_LOAD;
            reason = "Large multi-shot session: expect substantial render time and storage use";
        } else if (perFrame.safetyState() == CaptureSafetyState.HIGH_LOAD || perFrame.safetyState() == CaptureSafetyState.LIKELY_SAFE) {
            state = perFrame.safetyState();
            reason = perFrame.reason();
        } else {
            state = CaptureSafetyState.SAFE;
            reason = "Sequence resource estimates are within current limits";
        }
        return new SequencePreflight(sequence, requiredDisk, availableDisk, state, reason);
    }

    private static long fraction(long value, int percent) {
        if (value <= 0) return 0;
        return (value / 100L) * percent + ((value % 100L) * percent) / 100L;
    }
}
