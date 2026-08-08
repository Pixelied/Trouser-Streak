package dev.hypershot.shot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleplayer-only, temporary photographic time control using Minecraft 26.2's WorldClock system.
 * HyperShot never changes the daylight-cycle gamerule. It snapshots the overworld clock, pins that
 * clock only for the lifetime of the Time session, and restores the exact original total tick value.
 */
public final class TimeSceneController {
    private static final long DAY = 24_000L;
    private static final long REPIN_INTERVAL_NANOS = 40_000_000L;

    private final AtomicBoolean snapshotReady = new AtomicBoolean();
    private final AtomicBoolean operationFailed = new AtomicBoolean();
    private final AtomicLong originalTotalTicks = new AtomicLong();
    private final AtomicReference<String> error = new AtomicReference<>();

    private MinecraftServer server;
    private volatile boolean active;
    private volatile Long targetDayTime;
    private volatile Long targetTotalTicks;
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
        active = true;
        targetDayTime = null;
        targetTotalTicks = null;
        snapshotReady.set(false);
        operationFailed.set(false);
        error.set(null);
        candidate.execute(() -> {
            try {
                ServerLevel level = candidate.overworld();
                Holder.Reference<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
                originalTotalTicks.set(level.clockManager().getTotalTicks(overworldClock));
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
        long normalized = Math.floorMod(dayTime, DAY);
        long original = originalTotalTicks.get();
        long dayBase = original - Math.floorMod(original, DAY);
        targetDayTime = normalized;
        targetTotalTicks = Math.addExact(dayBase, normalized);
        schedulePin(targetTotalTicks);
    }

    /** Call each client tick while a Time frame is being prepared or captured. */
    public void tick(Minecraft minecraft, long nowNanos) {
        if (!active || operationFailed.get()) return;
        Long target = targetTotalTicks;
        if (target != null && nowNanos - lastRepinNanos >= REPIN_INTERVAL_NANOS) {
            schedulePin(target);
            lastRepinNanos = nowNanos;
        }
        if (minecraft == null || minecraft.level == null || minecraft.getSingleplayerServer() != server) restore();
    }

    public boolean clientObservedTarget(Minecraft minecraft) {
        Long target = targetDayTime;
        if (!snapshotReady() || target == null || minecraft == null || minecraft.level == null) return false;
        return Math.floorMod(minecraft.level.getOverworldClockTime(), DAY) == target;
    }

    public long originalTotalTicks() {
        if (!snapshotReady.get()) throw new IllegalStateException("No time snapshot available");
        return originalTotalTicks.get();
    }

    public Long targetDayTime() { return targetDayTime; }
    public boolean failed() { return operationFailed.get(); }
    public String errorMessage() { return Objects.requireNonNullElse(error.get(), "Time control failed"); }

    /** Idempotent best-effort restoration. */
    public void restore() {
        if (!active) return;
        active = false;
        Long original = snapshotReady.get() ? originalTotalTicks.get() : null;
        MinecraftServer restoreServer = server;
        targetDayTime = null;
        targetTotalTicks = null;
        server = null;
        if (original != null && restoreServer != null) {
            try {
                restoreServer.execute(() -> {
                    try {
                        ServerLevel level = restoreServer.overworld();
                        Holder.Reference<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
                        level.clockManager().setTotalTicks(overworldClock, original);
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            } catch (Throwable failure) {
                fail(failure);
            }
        }
    }

    public boolean active() { return active; }

    private void schedulePin(long targetTotal) {
        MinecraftServer currentServer = server;
        if (currentServer == null) return;
        try {
            currentServer.execute(() -> {
                try {
                    ServerLevel level = currentServer.overworld();
                    Holder.Reference<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
                    level.clockManager().setTotalTicks(overworldClock, targetTotal);
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
