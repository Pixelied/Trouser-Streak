package dev.hypershot.core.camera;

public record SequenceEstimate(int frameCount, long totalOutputPixels, long totalRenderedPixels,
                               long totalFinalBytes, long worstTemporaryBytes, long peakHeapBytes) {
    public SequenceEstimate {
        if (frameCount < 1 || totalOutputPixels < 0 || totalRenderedPixels < 0 || totalFinalBytes < 0 || worstTemporaryBytes < 0 || peakHeapBytes < 0) {
            throw new IllegalArgumentException("Invalid sequence estimate");
        }
    }
}
