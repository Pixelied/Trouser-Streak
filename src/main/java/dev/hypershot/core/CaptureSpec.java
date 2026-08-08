package dev.hypershot.core;

public record CaptureSpec(int width, int height, int tileSize, int overlap, int temporalSamples, OutputFormat format) {
    public CaptureSpec {
        if (width <= 0 || height <= 0 || tileSize < 64 || overlap < 0 || temporalSamples <= 0 || format == null) {
            throw new IllegalArgumentException("Invalid capture specification");
        }
        if (!format.supports(new Resolution(width, height))) throw new IllegalArgumentException("Codec dimension limit exceeded");
    }
}
