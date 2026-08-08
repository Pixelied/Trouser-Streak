package dev.hypershot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImageHeaderProbeTest {
    @TempDir Path directory;

    @Test
    void readsPngDimensionsWithoutPixelDecode() throws Exception {
        Path image = directory.resolve("huge.png");
        Files.write(image, pngHeader(20_000, 10_000));

        var info = ImageHeaderProbe.probe(image).orElseThrow();
        assertEquals(20_000, info.width());
        assertEquals(10_000, info.height());
        assertEquals("PNG", info.format());
    }

    @Test
    void walksJpegSegmentsUntilStartOfFrame() throws Exception {
        Path image = directory.resolve("photo.jpg");
        Files.write(image, jpegHeader(7_680, 4_320));

        var info = ImageHeaderProbe.probe(image).orElseThrow();
        assertEquals(7_680, info.width());
        assertEquals(4_320, info.height());
        assertEquals("JPEG", info.format());
    }

    @Test
    void rejectsTruncatedAndUnknownFilesCleanly() throws Exception {
        Path truncated = directory.resolve("broken.png");
        Path unknown = directory.resolve("notes.txt");
        Files.write(truncated, new byte[] {(byte) 0x89, 0x50, 0x4E});
        Files.writeString(unknown, "not an image");

        assertTrue(ImageHeaderProbe.probe(truncated).isEmpty());
        assertTrue(ImageHeaderProbe.probe(unknown).isEmpty());
        assertTrue(ImageHeaderProbe.probe(directory.resolve("missing.png")).isEmpty());
    }

    private static byte[] pngHeader(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            output.writeInt(13);
            output.writeInt(0x49484452);
            output.writeInt(width);
            output.writeInt(height);
            output.writeByte(8);
            output.writeByte(6);
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
            output.writeInt(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] jpegHeader(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeShort(0xFFD8);
            output.writeShort(0xFFE0);
            output.writeShort(16);
            output.write(new byte[14]);
            output.writeShort(0xFFC2);
            output.writeShort(17);
            output.writeByte(8);
            output.writeShort(height);
            output.writeShort(width);
            output.writeByte(3);
            output.write(new byte[9]);
            output.writeShort(0xFFD9);
        }
        return bytes.toByteArray();
    }
}
