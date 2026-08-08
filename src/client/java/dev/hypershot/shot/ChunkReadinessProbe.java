package dev.hypershot.shot;

import dev.hypershot.core.camera.ChunkReadiness;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Best-effort client-observable chunk readiness. It never claims terrain the client has not received is ready. */
public final class ChunkReadinessProbe {
    private final int radiusChunks;

    public ChunkReadinessProbe(int radiusChunks) {
        if (radiusChunks < 0 || radiusChunks > 8) throw new IllegalArgumentException("Chunk readiness radius must be 0-8");
        this.radiusChunks = radiusChunks;
    }

    public ChunkReadiness probe(Minecraft minecraft, long nowNanos, long deadlineNanos) {
        Objects.requireNonNull(minecraft);
        if (minecraft.level == null || minecraft.player == null) return new ChunkReadiness(0, 1, nowNanos >= deadlineNanos);
        int centerX = minecraft.player.chunkPosition().x();
        int centerZ = minecraft.player.chunkPosition().z();
        int loaded = 0;
        int side = radiusChunks * 2 + 1;
        int total = side * side;
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                if (minecraft.level.hasChunk(centerX + dx, centerZ + dz)) loaded++;
            }
        }
        return new ChunkReadiness(loaded, total, nowNanos >= deadlineNanos && loaded < total);
    }
}
