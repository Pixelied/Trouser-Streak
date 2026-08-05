package dev.hypershot.core;

import java.io.IOException;
import java.nio.file.Path;

final class StreamingPngEncoder implements ImageEncoder {
    @Override public OutputFormat format() { return OutputFormat.PNG; }
    @Override public String extension() { return ".png"; }
    @Override public boolean isStreaming() { return true; }

    @Override
    public void validate(int width, int height, EncoderOptions options) {
        Resolution resolution = new Resolution(width, height);
        if (!format().supports(resolution)) throw new IllegalArgumentException("PNG dimensions are unsupported");
        CheckedMath.bytesForPixels(width, height, 4);
    }

    @Override
    public void encode(DiskBackedRgbaSurface surface, int width, int height, Path output, EncoderOptions options) throws IOException {
        validate(width, height, options);
        surface.encodePng(output, options.pngCompression());
    }
}
