package dev.hypershot.core.camera;

import dev.hypershot.core.CaptureEstimate;
import dev.hypershot.core.CheckedMath;

public final class SequenceEstimateCalculator {
    private SequenceEstimateCalculator() {}

    public static SequenceEstimate multiply(CaptureEstimate perFrame, int frameCount) {
        if (perFrame == null || frameCount < 1) throw new IllegalArgumentException("Invalid sequence workload");
        return new SequenceEstimate(frameCount,
                CheckedMath.multiply(perFrame.outputPixels(), frameCount),
                CheckedMath.multiply(perFrame.renderedPixels(), frameCount),
                CheckedMath.multiply(perFrame.estimatedFinalBytes(), frameCount),
                perFrame.temporaryBytes(), perFrame.peakHeapBytes());
    }
}
