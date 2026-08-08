package dev.hypershot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExtremeResolutionPipelineTest {
    private static final int WIDTH = 10_000;
    private static final int HEIGHT = 10_000;
    private static final long RGBA_BYTES = 400_000_000L;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void streamsTenThousandSquarePngWithoutAllocatingTheFinalImageInHeap() throws Exception {
        Path directory = Files.createTempDirectory("hypershot-10k-");
        Path spool = directory.resolve("surface.rgba");
        Path output = directory.resolve("capture.png");

        try {
            byte[] row = patternedRow();
            assertEquals(WIDTH * 4, row.length, "Only one 40 KB RGBA row is retained by the test");

            try (DiskBackedRgbaSurface surface = new DiskBackedRgbaSurface(spool, WIDTH, HEIGHT)) {
                assertEquals(RGBA_BYTES, surface.byteSize());
                assertEquals(RGBA_BYTES, Files.size(spool));

                surface.writeRow(0, row);
                surface.writeRow(HEIGHT - 1, row);
                surface.encodePng(output, 1);
            }

            assertTrue(Files.size(output) > 0, "Streaming encoder must produce a non-empty PNG");
            assertPngHeader(output, WIDTH, HEIGHT);
        } finally {
            deleteRecursively(directory);
        }
    }

    private static byte[] patternedRow() {
        byte[] row = new byte[WIDTH * 4];
        for (int x = 0; x < WIDTH; x++) {
            int offset = x * 4;
            row[offset] = (byte) (x & 0xFF);
            row[offset + 1] = (byte) ((x >>> 3) & 0xFF);
            row[offset + 2] = (byte) ((x >>> 6) & 0xFF);
            row[offset + 3] = (byte) 0xFF;
        }
        return row;
    }

    private static void assertPngHeader(Path image, int expectedWidth, int expectedHeight) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(image))) {
            byte[] signature = input.readNBytes(PNG_SIGNATURE.length);
            assertArrayEquals(PNG_SIGNATURE, signature);
            assertEquals(13, input.readInt(), "IHDR payload length");
            assertEquals(0x49484452, input.readInt(), "IHDR chunk type");
            assertEquals(expectedWidth, input.readInt());
            assertEquals(expectedHeight, input.readInt());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
