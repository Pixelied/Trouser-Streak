package dev.hypershot.core;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** Standard-library JPEG codec. Intentionally bounded because the JDK writer requires a raster. */
final class BufferedJpegEncoder implements ImageEncoder {
    private static final long MAX_BUFFERED_PIXELS = 20_000_000L;

    @Override public OutputFormat format() { return OutputFormat.JPEG; }
    @Override public String extension() { return ".jpg"; }
    @Override public boolean isStreaming() { return false; }

    @Override
    public void validate(int width, int height, EncoderOptions options) {
        Resolution resolution = new Resolution(width, height);
        if (!format().supports(resolution)) throw new IllegalArgumentException("JPEG dimensions exceed the codec limit");
        long pixels = CheckedMath.multiply(width, height);
        if (pixels > MAX_BUFFERED_PIXELS) {
            throw new IllegalArgumentException("JPEG output above 20 million pixels is disabled because the bundled JDK codec requires a full RGB raster; use streaming PNG for extreme captures");
        }
        long required = CheckedMath.add(CheckedMath.bytesForPixels(width, height, 4), CheckedMath.multiply(width, 8L));
        if (required > options.maximumHeapBytes()) throw new IllegalArgumentException("JPEG raster exceeds the configured encoder heap budget");
    }

    @Override
    public void encode(DiskBackedRgbaSurface surface, int width, int height, Path output, EncoderOptions options) throws IOException {
        validate(width, height, options);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        byte[] rgba = new byte[CheckedMath.checkedInt(CheckedMath.multiply(width, 4), "JPEG row bytes")];
        int[] rgb = new int[width];
        for (int y = 0; y < height; y++) {
            surface.readRow(y, rgba);
            for (int x = 0; x < width; x++) {
                int p = x * 4;
                rgb[x] = (Byte.toUnsignedInt(rgba[p]) << 16) | (Byte.toUnsignedInt(rgba[p + 1]) << 8) | Byte.toUnsignedInt(rgba[p + 2]);
            }
            image.setRGB(0, y, width, 1, rgb, 0, width);
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG ImageIO writer is available");
        ImageWriter writer = writers.next();
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output.toFile())) {
            if (stream == null) throw new IOException("Unable to create JPEG output stream");
            writer.setOutput(stream);
            ImageWriteParam parameter = writer.getDefaultWriteParam();
            if (parameter.canWriteCompressed()) {
                parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameter.setCompressionQuality(options.jpegQuality());
            }
            if (parameter.canWriteProgressive()) parameter.setProgressiveMode(options.progressive() ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
            writer.write(null, new IIOImage(image, null, null), parameter);
        } finally {
            writer.dispose();
            image.flush();
        }
    }
}
