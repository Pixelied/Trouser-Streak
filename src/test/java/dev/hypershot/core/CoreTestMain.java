package dev.hypershot.core;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoreTestMain {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testCheckedMath();
        testResolution();
        testTileLayout();
        testProjection();
        testBudgets();
        testFilenameSanitization();
        testRecoveryManifest();
        testGalleryFilter();
        testRestoration();
        testStreamingPng();
        testCodecRegistryAndJpeg();
        testDiskBackedSurface();
        testBoundedThumbnail();
        testProgressAndCancellation();
        testAtomicOutput();
        System.out.println("HyperShot core tests: PASS (" + assertions + " assertions)");
    }

    private static void testCheckedMath() {
        eq(400_000_000L, CheckedMath.bytesForPixels(10_000, 10_000, 4), "10K RGBA bytes");
        throwsType(ArithmeticException.class, () -> CheckedMath.multiply(Long.MAX_VALUE, 2), "overflow rejected");
        throwsType(IllegalArgumentException.class, () -> CheckedMath.bytesForPixels(0, 10, 4), "zero width rejected");
    }

    private static void testResolution() {
        eq(new Resolution(3840, 2160), Resolution.parse("3840x2160"), "resolution parse");
        eq("16:9", new Resolution(3840, 2160).reducedAspectRatio(), "aspect reduction");
        eq(new Resolution(2000, 1125), AspectRatioLock.lockWidth(2000, new Resolution(16, 9)), "aspect lock");
        throwsType(IllegalArgumentException.class, () -> Resolution.parse("999999999999x2"), "oversize parse rejected");
    }

    private static void testTileLayout() {
        TileLayout layout = TileLayout.create(10_000, 7_001, 4096, 64);
        eq(3, layout.columns(), "columns");
        eq(2, layout.rows(), "rows");
        eq(6, layout.tiles().size(), "tile count");
        Tile edge = layout.tiles().get(5);
        eq(1808, edge.contentWidth(), "edge width");
        eq(2905, edge.contentHeight(), "edge height");
        check(edge.renderWidth() <= 4096 + 128, "overlap bounded render width");
        eq(100_00L * 7_001L, layout.totalPixels(), "pixel total");
    }

    private static void testProjection() {
        TileLayout layout = TileLayout.create(10_000, 10_000, 4096, 32);
        Tile center = layout.tiles().get(4);
        ProjectionWindow w = TileProjectionCalculator.window(center, 10_000, 10_000, 70.0, 0.05, 1000.0);
        check(w.left() < w.right(), "projection horizontal order");
        check(w.bottom() < w.top(), "projection vertical order");
        near(0.05, w.near(), 1e-12, "near preserved");
        near(1000.0, w.far(), 1e-9, "far preserved");
        ProjectionWindow whole = TileProjectionCalculator.fullFrame(16, 9, 70, 0.05, 1000);
        near(-whole.right(), whole.left(), 1e-12, "full projection symmetric x");
        near(-whole.top(), whole.bottom(), 1e-12, "full projection symmetric y");
    }

    private static void testBudgets() {
        CaptureEstimate estimate = CaptureEstimator.estimate(new CaptureSpec(20_000, 20_000, 2048, 4, 4, OutputFormat.PNG));
        eq(400_000_000L, estimate.outputPixels(), "output pixels");
        check(estimate.peakHeapBytes() < 512L * 1024 * 1024, "bounded peak heap");
        check(estimate.temporaryBytes() > 1_000_000_000L, "temp estimate includes spool");
        Resolution safe = ResolutionAdvisor.maximumSafe(new CapabilitySnapshot(
                2L << 30, 1L << 30, 20L << 30, 1L << 30, 8192, 4096),
                new Resolution(20_000, 20_000), 4, 1);
        check(safe.width() >= 8192 && safe.height() >= 8192, "safe advisor uses tiling");
        check(CheckedMath.bytesForPixels(safe.width(), safe.height(), 4) < (20L << 30), "safe advisor respects disk");
    }

    private static void testFilenameSanitization() {
        eq("CON_", FilenamePolicy.sanitizeStem("CON"), "Windows reserved name");
        eq("a_b_c", FilenamePolicy.sanitizeStem("a/b:c"), "invalid characters");
        check(!FilenamePolicy.sanitizeStem(" hello. ").endsWith("."), "trailing dot removed");
        String expanded = FilenamePolicy.expand("{date}_{width}x{height}_{id}", Map.of(
                "date", "2026-08-04", "width", "10000", "height", "10000", "id", "abc"));
        eq("2026-08-04_10000x10000_abc", expanded, "template expansion");
    }

    private static void testRecoveryManifest() {
        RecoveryManifest manifest = RecoveryManifest.create("id-1", Path.of("tmp.part"), 3, 2);
        RecoveryManifest updated = manifest.withCompleted(0).withCompleted(4);
        check(updated.isCompleted(0), "first tile complete");
        check(updated.isCompleted(4), "later tile complete");
        check(!updated.isCompleted(2), "other tile incomplete");
        eq(updated, RecoveryManifest.fromJson(updated.toJson()), "manifest round trip");
    }

    private static void testGalleryFilter() {
        List<GalleryEntry> entries = List.of(
                new GalleryEntry("a", "Castle.png", 3840, 2160, 10, true, List.of("build"), "TILED"),
                new GalleryEntry("b", "cave.jpg", 1920, 1080, 20, false, List.of("dark"), "NATIVE"));
        eq(1, GalleryQuery.apply(entries, new GalleryQuery("castle", true, null, null)).size(), "name/favorite filter");
        eq("b", GalleryQuery.sort(entries, GallerySort.FILE_SIZE_DESC).getFirst().id(), "size sort");
        eq(1, GalleryQuery.apply(entries, new GalleryQuery("build", null, 3000, "TILED")).size(), "tag/resolution/mode filter");
    }

    private static void testRestoration() throws Exception {
        AtomicInteger value = new AtomicInteger(1);
        try {
            try (StateRestorationScope scope = new StateRestorationScope()) {
                int old = value.get();
                scope.change(() -> value.set(9), () -> value.set(old));
                eq(9, value.get(), "state changed");
                throw new IllegalStateException("boom");
            }
        } catch (IllegalStateException expected) {
            eq(1, value.get(), "state restored after exception");
        }
        AtomicInteger order = new AtomicInteger();
        try (StateRestorationScope scope = new StateRestorationScope()) {
            scope.onRestore(() -> eq(2, order.incrementAndGet(), "reverse restore second"));
            scope.onRestore(() -> eq(1, order.incrementAndGet(), "reverse restore first"));
        }
    }


    private static void testStreamingPng() throws Exception {
        Path dir = Files.createTempDirectory("hypershot-png-test");
        Path path = dir.resolve("stream.png");
        try (StreamingPngWriter writer = new StreamingPngWriter(path, 257, 129, 6)) {
            byte[] row = new byte[257 * 4];
            for (int y = 0; y < 129; y++) {
                for (int x = 0; x < 257; x++) {
                    int i = x * 4;
                    row[i] = (byte) x;
                    row[i + 1] = (byte) y;
                    row[i + 2] = (byte) (x ^ y);
                    row[i + 3] = (byte) 255;
                }
                writer.writeRow(row);
            }
        }
        BufferedImage image = ImageIO.read(path.toFile());
        eq(257, image.getWidth(), "stream PNG width");
        eq(129, image.getHeight(), "stream PNG height");
        eq(0xffc88048, image.getRGB(200, 128), "stream PNG pixel");
        check(Files.size(path) > 1000, "stream PNG written");
    }


    private static void testCodecRegistryAndJpeg() throws Exception {
        Path dir = Files.createTempDirectory("hypershot-jpeg-test");
        Path spool = dir.resolve("capture.rgba");
        try (DiskBackedRgbaSurface surface = new DiskBackedRgbaSurface(spool, 257, 129)) {
            byte[] row = new byte[257 * 4];
            for (int y = 0; y < 129; y++) {
                for (int x = 0; x < 257; x++) {
                    int i = x * 4;
                    row[i] = (byte) x;
                    row[i + 1] = (byte) y;
                    row[i + 2] = (byte) (x ^ y);
                    row[i + 3] = (byte) 255;
                }
                surface.writeRow(y, row);
            }
            ImageEncoder jpeg = ImageEncoders.forFormat(OutputFormat.JPEG);
            Path output = dir.resolve("image.jpg");
            jpeg.validate(257, 129, EncoderOptions.defaults());
            jpeg.encode(surface, 257, 129, output, new EncoderOptions(6, 0.92f, true, 128L * 1024 * 1024));
            BufferedImage image = ImageIO.read(output.toFile());
            eq(257, image.getWidth(), "JPEG width");
            eq(129, image.getHeight(), "JPEG height");
            check(Files.size(output) > 1000, "JPEG written");
            throwsType(IllegalArgumentException.class, () -> jpeg.validate(10000, 10000, EncoderOptions.defaults()), "huge buffered JPEG refused");
        }
        eq(OutputFormat.PNG, ImageEncoders.forFormat(OutputFormat.PNG).format(), "PNG codec registry");
    }

    private static void testDiskBackedSurface() throws Exception {
        Path dir = Files.createTempDirectory("hypershot-spool-test");
        TileLayout layout = TileLayout.create(70, 65, 64, 1);
        Path spool = dir.resolve("capture.rgba");
        try (DiskBackedRgbaSurface surface = new DiskBackedRgbaSurface(spool, 70, 65)) {
            for (Tile tile : layout.tiles()) {
                byte[] render = new byte[tile.renderWidth() * tile.renderHeight() * 4];
                for (int ry = 0; ry < tile.renderHeight(); ry++) {
                    int globalY = tile.renderY() + ry;
                    for (int rx = 0; rx < tile.renderWidth(); rx++) {
                        int globalX = tile.renderX() + rx;
                        int i = (ry * tile.renderWidth() + rx) * 4;
                        render[i] = (byte) globalX;
                        render[i + 1] = (byte) globalY;
                        render[i + 2] = (byte) 99;
                        render[i + 3] = (byte) 255;
                    }
                }
                surface.writeTile(tile, render);
            }
            byte[] row = new byte[70 * 4];
            surface.readRow(64, row);
            eq(69, Byte.toUnsignedInt(row[69 * 4]), "spool edge x");
            eq(64, Byte.toUnsignedInt(row[69 * 4 + 1]), "spool edge y");
            Path encoded = dir.resolve("encoded.png");
            surface.encodePng(encoded, 4);
            BufferedImage image = ImageIO.read(encoded.toFile());
            eq(0xff454063, image.getRGB(69, 64), "spool encoded pixel");
        }
    }


    private static void testBoundedThumbnail() throws Exception {
        Path dir = Files.createTempDirectory("hypershot-thumbnail-test");
        Path spool = dir.resolve("capture.rgba");
        try (DiskBackedRgbaSurface surface = new DiskBackedRgbaSurface(spool, 400, 200)) {
            byte[] row = new byte[400 * 4];
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 400; x++) {
                    int i = x * 4;
                    row[i] = (byte) (x / 2);
                    row[i + 1] = (byte) y;
                    row[i + 2] = (byte) 77;
                    row[i + 3] = (byte) 255;
                }
                surface.writeRow(y, row);
            }
            Path preview = dir.resolve("preview.png");
            ThumbnailGenerator.writePng(surface, 400, 200, preview, 100, 100, 4);
            BufferedImage image = ImageIO.read(preview.toFile());
            eq(100, image.getWidth(), "thumbnail width");
            eq(50, image.getHeight(), "thumbnail aspect height");
            eq(0xffc7c64d, image.getRGB(99, 49), "thumbnail sampled pixel");
        }
    }

    private static void testProgressAndCancellation() {
        CaptureProgress progress = new CaptureProgress(12, 1000);
        progress.begin(CapturePhase.CAPTURING_TILES, 1_000_000_000L);
        progress.tileCompleted(250, 2_000_000_000L);
        progress.tileCompleted(250, 3_000_000_000L);
        eq(2, progress.snapshot(3_000_000_000L).tilesCompleted(), "progress tile count");
        near(0.5, progress.snapshot(3_000_000_000L).pixelsFraction(), 1e-9, "progress pixel fraction");
        check(progress.snapshot(3_000_000_000L).eta().compareTo(Duration.ZERO) > 0, "rolling ETA positive");
        CancellationToken token = new CancellationToken();
        check(!token.isCancelled(), "token starts active");
        token.cancel("escape held");
        check(token.isCancelled(), "token cancelled");
        eq("escape held", token.reason(), "cancellation reason");
        throwsType(CaptureCancelledException.class, token::throwIfCancelled, "cancel throws");
    }

    private static void testAtomicOutput() throws Exception {
        Path dir = Files.createTempDirectory("hypershot-atomic-test");
        Path target = dir.resolve("shot.png");
        AtomicBoolean sawTemporary = new AtomicBoolean();
        AtomicOutput.write(target, temporary -> {
            sawTemporary.set(!temporary.equals(target) && temporary.getFileName().toString().endsWith(".part"));
            Files.writeString(temporary, "done");
        });
        check(sawTemporary.get(), "atomic writer uses part file");
        eq("done", Files.readString(target), "atomic output content");
        check(Files.list(dir).noneMatch(p -> p.getFileName().toString().endsWith(".part")), "part removed");
        Path unique = AtomicOutput.uniqueSibling(target);
        check(!unique.equals(target), "collision creates unique sibling");
    }

    private static void check(boolean value, String name) { assertions++; if (!value) throw new AssertionError(name); }
    private static void eq(Object expected, Object actual, String name) { assertions++; if (!expected.equals(actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }
    private static void near(double expected, double actual, double epsilon, String name) { assertions++; if (Math.abs(expected - actual) > epsilon) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }
    private static void throwsType(Class<? extends Throwable> type, ThrowingRunnable action, String name) { assertions++; try { action.run(); throw new AssertionError(name + ": did not throw"); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(name + ": " + t, t); } }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
