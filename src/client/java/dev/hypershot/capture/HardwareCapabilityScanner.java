package dev.hypershot.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hypershot.core.CapabilitySnapshot;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HardwareCapabilityScanner {
    public CapabilitySnapshot scan(Path outputDirectory, long freeDiskMarginBytes) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        long maxHeap = runtime.maxMemory();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();
        FileStore store = Files.getFileStore(outputDirectory);
        long freeHeap = Math.max(0L, maxHeap - usedHeap);
        long usableDisk = Math.max(0L, store.getUsableSpace() - freeDiskMarginBytes);
        int maxTexture = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
        int preferredTile = Math.max(512, Math.min(4096, maxTexture));
        return new CapabilitySnapshot(maxHeap, freeHeap, usableDisk, freeDiskMarginBytes, maxTexture, preferredTile);
    }

    public String backendName() {
        return RenderSystem.getDevice().getDeviceInfo().backendName();
    }

    public String gpuName() {
        return RenderSystem.getDevice().getDeviceInfo().name();
    }

    public String driverInfo() {
        return RenderSystem.getDevice().getDeviceInfo().driverInfo();
    }
}
