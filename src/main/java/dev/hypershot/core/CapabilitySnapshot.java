package dev.hypershot.core;

public record CapabilitySnapshot(long maxHeapBytes, long freeHeapBytes, long usableDiskBytes,
                                 long reservedDiskBytes, int maxRenderTargetDimension, int preferredTileSize) {
    public CapabilitySnapshot {
        if (maxHeapBytes <= 0 || freeHeapBytes < 0 || usableDiskBytes < 0 || reservedDiskBytes < 0 ||
                maxRenderTargetDimension < 64 || preferredTileSize < 64) throw new IllegalArgumentException("Invalid capabilities");
    }
}
