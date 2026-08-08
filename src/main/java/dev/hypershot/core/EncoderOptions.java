package dev.hypershot.core;

public record EncoderOptions(int pngCompression, float jpegQuality, boolean progressive, long maximumHeapBytes) {
    public EncoderOptions {
        if (pngCompression < 0 || pngCompression > 9) throw new IllegalArgumentException("PNG compression outside 0..9");
        if (!Float.isFinite(jpegQuality) || jpegQuality < 0.01f || jpegQuality > 1.0f) throw new IllegalArgumentException("JPEG quality outside 0.01..1.0");
        if (maximumHeapBytes < 16L * 1024 * 1024) throw new IllegalArgumentException("encoder heap budget too small");
    }

    public static EncoderOptions defaults() {
        return new EncoderOptions(6, 0.92f, true, 256L * 1024 * 1024);
    }
}
