package dev.hypershot.shot;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleplayer-only, temporary photographic time control.
 * It never changes the world's daylight-cycle gamerule; while active it pins only day time and restores the exact snapshot.
 */
public final class TimeSceneController {
    private static final long DAY = 24_000L;
    private static final long REPIN_INTERVAL_NANOS = 40_000_000L;

    private final AtomicBoolean snapshotReady = new AtomicBoolean();
    private final AtomicBoolean operationFailed = new AtomicBoolean();
    private final AtomicLong originalDayTime = new AtomicLong();
    private final AtomicReference<String> error = new AtomicReference<>();

    private MinecraftServer server;
    private ResourceKey<Level> dimension;
    private volatile boolean active;
    private volatile Long targetDayTime;
    private long lastRepinNanos;

    public boolean available(Minecraft minecraft) {
        return minecraft != null && minecraft.level != null && minecraft.getSingleplayerServer() != null;
    }

    public boolean begin(Minecraft minecraft) {
        Objects.requireNonNull(minecraft);
        if (active) return snapshotReady.get() && !operationFailed.get();
        MinecraftServer candidate = minecraft.getSingleplayerServer();
        if (candidate == null || minecraft.level == null) return false;
        server = candidate;
        dimension = minecraft.level.dimension();
        active = true;
        targetDayTime = null;
        snapshotReady.set(false);
        operationFailed.set(false);
        error.set(null);
        candidate.execute(() -> {
            try {
                ServerLevel level = candidate.getLevel(dimension);
                if (level == null) throw new IllegalStateException("Singleplayer dimension is unavailable");
                originalDayTime.set(level.getDayTime());
                snapshotReady.set(true);
            } catch (Throwable failure) {
                fail(failure);
            }
        });
        return true;
    }

    public boolean snapshotReady() {
        return active && snapshotReady.get() && !operationFailed.get();
    }

    public void applyTime(long dayTime) {
        if (!snapshotReady()) throw new IllegalStateException("Time scene snapshot is not ready");
        targetDayTime = Math.floorMod(dayTime, DAY);
        schedulePin(targetDayTime);
    }

    /** Call each client tick while a Time frame is being prepared or captured. */
    public void tick(Minecraft minecraft, long nowNanos) {
        if (!active || operationFailed.get()) return;
        Long target = targetDayTime;
        if (target != null && nowNanos - lastRepinNanos >= REPIN_INTERVAL_NANOS) {
            schedulePin(target);
            lastRepinNanos = nowNanos;
        }
        if (minecraft == null || minecraft.level == null || minecraft.getSingleplayerServer() != server) {
            restore();
        }
    }

    public boolean clientObservedTarget(Minecraft minecraft) {
        Long target = targetDayTime;
        if (!snapshotReady() || target == null || minecraft == null || minecraft.level == null) return false;
        return Math.floorMod(minecraft.level.getDayTime(), DAY) == target;
    }

    public long originalDayTime() {
        if (!snapshotReady.get()) throw new IllegalStateException("No time snapshot available");
        return originalDayTime.get();
    }

    public Long targetDayTime() {
        return targetDayTime;
    }

    public boolean failed() {
        return operationFailed.get();
    }

    public String errorMessage() {
        return Objects.requireNonNullElse(error.get(), "Time control failed");
    }

    /** Idempotent best-effort restoration. */
    public void restore() {
        if (!active) return;
        active = false;
        Long original = snapshotReady.get() ? originalDayTime.get() : null;
        MinecraftServer restoreServer = server;
        ResourceKey<Level> restoreDimension = dimension;
        targetDayTime = null;
        server = null;
        dimension = null;
        if (original != null && restoreServer != null && restoreDimension != null) {
            try {
                restoreServer.execute(() -> {
                    try {
                        ServerLevel level = restoreServer.getLevel(restoreDimension);
                        if (level != null) level.setDayTime(original);
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            } catch (Throwable failure) {
                fail(failure);
            }
        }
    }

    public boolean active() {
        return active;
    }

    private void schedulePin(long target) {
        MinecraftServer currentServer = server;
        ResourceKey<Level> currentDimension = dimension;
        if (currentServer == null || currentDimension == null) return;
        try {
            currentServer.execute(() -> {
                try {
                    ServerLevel level = currentServer.getLevel(currentDimension);
                    if (level == null) throw new IllegalStateException("Singleplayer dimension unloaded during Time capture");
                    level.setDayTime(target);
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void fail(Throwable failure) {
        operationFailed.set(true);
        error.set(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }
}
