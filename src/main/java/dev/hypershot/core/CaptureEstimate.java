package dev.hypershot.core;

public record CaptureEstimate(long outputPixels, long renderedPixels, long peakHeapBytes, long temporaryBytes, long estimatedFinalBytes) {}
