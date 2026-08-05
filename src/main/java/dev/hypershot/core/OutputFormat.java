package dev.hypershot.core;

public enum OutputFormat {
    PNG(true, true, Integer.MAX_VALUE, Integer.MAX_VALUE),
    JPEG(false, false, 65_535, 65_535);

    private final boolean alpha;
    private final boolean streaming;
    private final int maxWidth;
    private final int maxHeight;

    OutputFormat(boolean alpha, boolean streaming, int maxWidth, int maxHeight) {
        this.alpha = alpha;
        this.streaming = streaming;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public boolean supportsAlpha() { return alpha; }
    public boolean supportsStreaming() { return streaming; }
    public boolean supports(Resolution resolution) { return resolution.width() <= maxWidth && resolution.height() <= maxHeight; }
}
