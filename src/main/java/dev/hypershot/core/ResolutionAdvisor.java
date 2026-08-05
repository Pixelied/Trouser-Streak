package dev.hypershot.core;

public final class ResolutionAdvisor {
    private ResolutionAdvisor() {}

    public static Resolution maximumSafe(CapabilitySnapshot capabilities, Resolution requested,
                                         int bytesPerPixel, int temporalSamples) {
        if (bytesPerPixel <= 0 || temporalSamples <= 0) throw new IllegalArgumentException("capture parameters");
        long availableDisk = Math.max(0L, capabilities.usableDiskBytes() - capabilities.reservedDiskBytes());
        long availableHeap = Math.max(64L << 20, Math.min(capabilities.freeHeapBytes(), capabilities.maxHeapBytes() / 2));
        int tile = Math.min(capabilities.preferredTileSize(), capabilities.maxRenderTargetDimension());
        double scale = 1.0;
        while (scale > 0.01) {
            int width = Math.max(1, (int) Math.floor(requested.width() * scale));
            int height = Math.max(1, (int) Math.floor(requested.height() * scale));
            CaptureEstimate estimate = CaptureEstimator.estimate(new CaptureSpec(width, height, tile, 32, temporalSamples, OutputFormat.PNG));
            if (estimate.peakHeapBytes() <= availableHeap &&
                    CheckedMath.add(estimate.temporaryBytes(), estimate.estimatedFinalBytes()) <= availableDisk &&
                    CheckedMath.bytesForPixels(width, height, bytesPerPixel) <= availableDisk) {
                return new Resolution(width, height);
            }
            scale *= 0.9;
        }
        throw new IllegalStateException("No safe positive resolution for current budgets");
    }
}
