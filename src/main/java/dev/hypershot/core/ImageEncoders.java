package dev.hypershot.core;

public final class ImageEncoders {
    private static final ImageEncoder PNG = new StreamingPngEncoder();
    private static final ImageEncoder JPEG = new BufferedJpegEncoder();
    private ImageEncoders() {}

    public static ImageEncoder forFormat(OutputFormat format) {
        return switch (format) {
            case PNG -> PNG;
            case JPEG -> JPEG;
        };
    }
}
