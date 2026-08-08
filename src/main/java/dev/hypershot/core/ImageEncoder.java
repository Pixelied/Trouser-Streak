package dev.hypershot.core;

import java.io.IOException;
import java.nio.file.Path;

public interface ImageEncoder {
    OutputFormat format();
    String extension();
    boolean isStreaming();
    void validate(int width, int height, EncoderOptions options);
    void encode(DiskBackedRgbaSurface surface, int width, int height, Path output, EncoderOptions options) throws IOException;
}
