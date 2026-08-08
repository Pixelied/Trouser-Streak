package dev.hypershot.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads only image container headers. It never allocates a pixel raster or decodes image contents.
 */
public final class ImageHeaderProbe {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final int MAX_JPEG_SEGMENT_BYTES = 65_533;

    private ImageHeaderProbe() {}

    public static Optional<ImageInfo> probe(Path path) {
        if (path == null || !Files.isRegularFile(path)) return Optional.empty();
        try (InputStream raw = Files.newInputStream(path);
             BufferedInputStream buffered = new BufferedInputStream(raw, 4096)) {
            buffered.mark(PNG_SIGNATURE.length + 32);
            Optional<ImageInfo> png = probePng(buffered);
            if (png.isPresent()) return png;
            buffered.reset();
            return probeJpeg(buffered);
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ImageInfo> probePng(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        byte[] signature = data.readNBytes(PNG_SIGNATURE.length);
        if (signature.length != PNG_SIGNATURE.length) return Optional.empty();
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (signature[index] != PNG_SIGNATURE[index]) return Optional.empty();
        }
        int chunkLength = data.readInt();
        int chunkType = data.readInt();
        if (chunkLength != 13 || chunkType != 0x49484452) return Optional.empty();
        int width = data.readInt();
        int height = data.readInt();
        return valid(width, height, "PNG");
    }

    private static Optional<ImageInfo> probeJpeg(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readUnsignedByte() != 0xFF || data.readUnsignedByte() != 0xD8) return Optional.empty();

        while (true) {
            int prefix;
            do {
                prefix = data.readUnsignedByte();
            } while (prefix != 0xFF);

            int marker;
            do {
                marker = data.readUnsignedByte();
            } while (marker == 0xFF);

            if (marker == 0xD9 || marker == 0xDA) return Optional.empty();
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) continue;

            int segmentLength = data.readUnsignedShort();
            if (segmentLength < 2 || segmentLength - 2 > MAX_JPEG_SEGMENT_BYTES) return Optional.empty();
            int payloadLength = segmentLength - 2;
            if (isStartOfFrame(marker)) {
                if (payloadLength < 5) return Optional.empty();
                data.readUnsignedByte();
                int height = data.readUnsignedShort();
                int width = data.readUnsignedShort();
                return valid(width, height, "JPEG");
            }
            skipExactly(data, payloadLength);
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return switch (marker) {
            case 0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> true;
            default -> false;
        };
    }

    private static void skipExactly(DataInputStream input, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = input.skipBytes(remaining);
            if (skipped <= 0) throw new EOFException("Unexpected end of image header");
            remaining -= skipped;
        }
    }

    private static Optional<ImageInfo> valid(int width, int height, String format) {
        if (width <= 0 || height <= 0) return Optional.empty();
        return Optional.of(new ImageInfo(width, height, format.toUpperCase(Locale.ROOT)));
    }

    public record ImageInfo(int width, int height, String format) {}
}
