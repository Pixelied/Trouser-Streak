package dev.hypershot.diagnostics;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hypershot.util.HyperShotPaths;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.OffsetDateTime;

public record DiagnosticsSnapshot(OffsetDateTime capturedAt, String backend, String gpu, String vendor,
                                  String driver, int maximumTextureSize, long heapUsed, long heapMaximum,
                                  long outputUsableBytes, boolean outputWritable, String javaVersion,
                                  String operatingSystem) {
    public static DiagnosticsSnapshot capture(HyperShotPaths paths) {
        var info = RenderSystem.getDevice().getDeviceInfo();
        Runtime runtime = Runtime.getRuntime();
        long usable = -1;
        boolean writable = false;
        try {
            FileStore store = Files.getFileStore(paths.captures());
            usable = store.getUsableSpace();
            writable = Files.isWritable(paths.captures());
        } catch (Exception ignored) {}
        return new DiagnosticsSnapshot(OffsetDateTime.now(), info.backendName(), info.name(), info.vendorName(),
                info.driverInfo(), info.limits().maxTextureSize(), runtime.totalMemory() - runtime.freeMemory(),
                runtime.maxMemory(), usable, writable, System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }
}
