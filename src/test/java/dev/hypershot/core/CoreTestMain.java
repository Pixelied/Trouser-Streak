package dev.hypershot.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoreTestMain {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        mathResolutionAndLayout();
        estimatesNamesAndRecovery();
        stateOutputAndCancellation();
        responsiveUi();
        System.out.println("HyperShot core tests: PASS (" + assertions + " assertions)");
    }

    private static void mathResolutionAndLayout() {
        eq(400_000_000L, CheckedMath.bytesForPixels(10_000, 10_000, 4), "10K RGBA bytes");
        throwsType(ArithmeticException.class, () -> CheckedMath.multiply(Long.MAX_VALUE, 2), "overflow rejected");
        throwsType(IllegalArgumentException.class, () -> CheckedMath.bytesForPixels(0, 10, 4), "zero rejected");
        Resolution resolution = Resolution.parse("3840x2160");
        eq(new Resolution(3840, 2160), resolution, "resolution parse");
        eq("16:9", resolution.reducedAspectRatio(), "aspect reduction");
        eq(new Resolution(2000, 1125), AspectRatioLock.lockWidth(2000, new Resolution(16, 9)), "aspect lock");

        TileLayout layout = TileLayout.create(10_000, 7_001, 4096, 64);
        eq(3, layout.columns(), "columns");
        eq(2, layout.rows(), "rows");
        eq(6, layout.tiles().size(), "tile count");
        eq(70_010_000L, layout.totalPixels(), "pixel total");
        Tile edge = layout.tiles().get(5);
        eq(1808, edge.contentWidth(), "edge width");
        eq(2905, edge.contentHeight(), "edge height");
        check(edge.renderWidth() <= 4224 && edge.renderHeight() <= 4224, "overlap bounded");
        for (Tile tile : layout.tiles()) {
            check(tile.contentWidth() > 0 && tile.contentHeight() > 0, "positive tile content");
            check(tile.renderWidth() >= tile.contentWidth() && tile.renderHeight() >= tile.contentHeight(), "render covers content");
        }
        ProjectionWindow frame = TileProjectionCalculator.fullFrame(16, 9, 70, 0.05, 1000);
        near(-frame.right(), frame.left(), 1e-12, "frame symmetric x");
        near(-frame.top(), frame.bottom(), 1e-12, "frame symmetric y");
        ProjectionWindow tile = TileProjectionCalculator.window(edge, 10_000, 7_001, 70, 0.05, 1000);
        check(tile.left() < tile.right() && tile.bottom() < tile.top(), "tile frustum order");
        near(0.05, tile.near(), 1e-12, "near preserved");
        near(1000, tile.far(), 1e-9, "far preserved");
    }

    private static void estimatesNamesAndRecovery() {
        CaptureEstimate estimate = CaptureEstimator.estimate(new CaptureSpec(20_000, 20_000, 2048, 4, 4, OutputFormat.PNG));
        eq(400_000_000L, estimate.outputPixels(), "output pixels");
        check(estimate.peakHeapBytes() < 512L * 1024 * 1024, "bounded heap");
        check(estimate.temporaryBytes() > 1_000_000_000L, "disk spool included");
        Resolution safe = ResolutionAdvisor.maximumSafe(
                new CapabilitySnapshot(2L << 30, 1L << 30, 20L << 30, 1L << 30, 8192, 4096),
                new Resolution(20_000, 20_000), 4, 1);
        check(safe.width() >= 8192 && safe.height() >= 8192, "safe tiled resolution");
        check(CheckedMath.bytesForPixels(safe.width(), safe.height(), 4) < (20L << 30), "disk respected");
        eq("CON_", FilenamePolicy.sanitizeStem("CON"), "reserved filename");
        eq("a_b_c", FilenamePolicy.sanitizeStem("a/b:c"), "invalid filename characters");
        eq("2026-08-05_10000x10000", FilenamePolicy.expand("{date}_{width}x{height}",
                Map.of("date", "2026-08-05", "width", "10000", "height", "10000")), "template expansion");

        RecoveryManifest manifest = RecoveryManifest.create("id", Path.of("capture.part"), 3, 2).withCompleted(0).withCompleted(4);
        check(manifest.isCompleted(0) && manifest.isCompleted(4), "completed recovery tiles");
        check(!manifest.isCompleted(2), "incomplete recovery tile");
        eq(manifest, RecoveryManifest.fromJson(manifest.toJson()), "recovery round trip");
        List<GalleryEntry> entries = List.of(
                new GalleryEntry("a", "Castle.png", 3840, 2160, 10, true, List.of("build"), "TILED"),
                new GalleryEntry("b", "cave.jpg", 1920, 1080, 20, false, List.of("dark"), "NATIVE"));
        eq(1, GalleryQuery.apply(entries, new GalleryQuery("castle", true, null, null)).size(), "gallery filter");
        eq("b", GalleryQuery.sort(entries, GallerySort.FILE_SIZE_DESC).get(0).id(), "gallery sort");
    }

    private static void stateOutputAndCancellation() throws Exception {
        AtomicInteger value = new AtomicInteger(1);
        try {
            try (StateRestorationScope scope = new StateRestorationScope()) {
                int original = value.get();
                scope.change(() -> value.set(9), () -> value.set(original));
                eq(9, value.get(), "state changed");
                throw new IllegalStateException("expected");
            }
        } catch (IllegalStateException expected) {
            eq(1, value.get(), "state restored");
        }

        Path dir = Files.createTempDirectory("hypershot-core");
        Path png = dir.resolve("stream.png");
        try (StreamingPngWriter writer = new StreamingPngWriter(png, 257, 129, 6)) {
            byte[] row = new byte[257 * 4];
            for (int y = 0; y < 129; y++) {
                fillRow(row, y);
                writer.writeRow(row);
            }
        }
        BufferedImage image = ImageIO.read(png.toFile());
        eq(257, image.getWidth(), "PNG width");
        eq(129, image.getHeight(), "PNG height");
        eq(0xffc88048, image.getRGB(200, 128), "PNG pixel");
        check(Files.size(png) > 1000, "PNG written");

        Path spool = dir.resolve("capture.rgba");
        try (DiskBackedRgbaSurface surface = new DiskBackedRgbaSurface(spool, 257, 129)) {
            byte[] row = new byte[257 * 4];
            for (int y = 0; y < 129; y++) {
                fillRow(row, y);
                surface.writeRow(y, row);
            }
            Path jpeg = dir.resolve("image.jpg");
            ImageEncoder encoder = ImageEncoders.forFormat(OutputFormat.JPEG);
            encoder.encode(surface, 257, 129, jpeg, new EncoderOptions(6, 0.92f, true, 128L * 1024 * 1024));
            BufferedImage decoded = ImageIO.read(jpeg.toFile());
            eq(257, decoded.getWidth(), "JPEG width");
            eq(129, decoded.getHeight(), "JPEG height");
            check(Files.size(jpeg) > 1000, "JPEG written");
            throwsType(IllegalArgumentException.class, () -> encoder.validate(10_000, 10_000, EncoderOptions.defaults()), "huge JPEG refused");
        }

        CaptureProgress progress = new CaptureProgress(12, 1000);
        progress.begin(CapturePhase.CAPTURING_TILES, 1_000_000_000L);
        progress.tileCompleted(250, 2_000_000_000L);
        progress.tileCompleted(250, 3_000_000_000L);
        CaptureProgressSnapshot snapshot = progress.snapshot(3_000_000_000L);
        eq(2, snapshot.tilesCompleted(), "progress tiles");
        near(0.5, snapshot.pixelsFraction(), 1e-9, "progress fraction");
        check(snapshot.eta().compareTo(Duration.ZERO) > 0, "positive ETA");
        CancellationToken token = new CancellationToken();
        token.cancel("escape held");
        check(token.isCancelled(), "token cancelled");
        eq("escape held", token.reason(), "cancel reason");
        throwsType(CaptureCancelledException.class, token::throwIfCancelled, "cancel throws");

        Path target = dir.resolve("atomic.png");
        AtomicBoolean partSeen = new AtomicBoolean();
        AtomicOutput.write(target, part -> {
            partSeen.set(!part.equals(target) && part.getFileName().toString().endsWith(".part"));
            Files.writeString(part, "done");
        });
        check(partSeen.get(), "atomic part used");
        eq("done", Files.readString(target), "atomic content");
        check(Files.list(dir).noneMatch(path -> path.getFileName().toString().endsWith(".part")), "part removed");
    }

    private static void responsiveUi() {
        UiLayout desktop = UiLayout.compute(1280, 720, true);
        check(!desktop.compact(), "desktop expanded");
        eq(184, desktop.sidebar().width(), "sidebar width");
        check(desktop.content().width() >= 720, "desktop content spacious");
        check(desktop.content().bottom() <= desktop.footer().top(), "desktop avoids footer");
        UiLayout compact = UiLayout.compute(420, 240, true);
        check(compact.compact(), "compact mode");
        eq(0, compact.sidebar().width(), "compact sidebar hidden");
        check(compact.content().width() >= 320, "compact content usable");
        check(compact.content().bottom() <= compact.footer().top(), "compact avoids footer");
        List<UiLayout.Rect> buttons = UiLayout.distribute(new UiLayout.Rect(12, 20, 396, 20), 5, 6);
        eq(5, buttons.size(), "button count");
        eq(12, buttons.get(0).left(), "row start");
        eq(408, buttons.get(4).right(), "row end");
        for (int i = 1; i < buttons.size(); i++) check(buttons.get(i - 1).right() <= buttons.get(i).left(), "buttons do not overlap");
        throwsType(IllegalArgumentException.class, () -> UiLayout.compute(0, 240, false), "invalid width rejected");
    }

    private static void fillRow(byte[] row, int y) {
        for (int x = 0; x < row.length / 4; x++) {
            int i = x * 4;
            row[i] = (byte) x;
            row[i + 1] = (byte) y;
            row[i + 2] = (byte) (x ^ y);
            row[i + 3] = (byte) 255;
        }
    }

    private static void check(boolean value, String name) { assertions++; if (!value) throw new AssertionError(name); }
    private static void eq(Object expected, Object actual, String name) { assertions++; if (!expected.equals(actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }
    private static void near(double expected, double actual, double epsilon, String name) { assertions++; if (Math.abs(expected - actual) > epsilon) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }
    private static void throwsType(Class<? extends Throwable> type, ThrowingRunnable action, String name) {
        assertions++;
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError(name + ": " + error, error); }
        throw new AssertionError(name + ": did not throw");
    }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
