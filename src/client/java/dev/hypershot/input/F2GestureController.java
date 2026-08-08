package dev.hypershot.input;

import dev.hypershot.config.HyperShotConfig;
import dev.hypershot.core.camera.F2Behavior;
import dev.hypershot.core.camera.F2GestureMachine;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Bridges Minecraft's real screenshot key mapping to HyperShot tap/hold semantics. */
public final class F2GestureController {
    private final HyperShotConfig config;
    private final Runnable instantPhoto;
    private final Runnable openViewfinder;
    private final Runnable toggleViewfinder;
    private final F2GestureMachine gesture = new F2GestureMachine();
    private boolean armed;

    public F2GestureController(HyperShotConfig config, Runnable instantPhoto, Runnable openViewfinder, Runnable toggleViewfinder) {
        this.config = Objects.requireNonNull(config);
        this.instantPhoto = Objects.requireNonNull(instantPhoto);
        this.openViewfinder = Objects.requireNonNull(openViewfinder);
        this.toggleViewfinder = Objects.requireNonNull(toggleViewfinder);
    }

    /** Called by ScreenshotMixin at the exact point vanilla responds to the configured screenshot key press. */
    public void onScreenshotKeyPressed(long nowNanos) {
        if (!config.replaceVanillaF2) return;
        switch (config.f2Behavior) {
            case INSTANT_ONLY -> {
                gesture.reset();
                armed = false;
                instantPhoto.run();
            }
            case VIEWFINDER_ONLY -> {
                gesture.reset();
                armed = false;
                openViewfinder.run();
            }
            case TAP_INSTANT_HOLD_VIEWFINDER -> {
                gesture.reset();
                gesture.update(true, nowNanos, thresholdNanos());
                armed = true;
            }
        }
    }

    public void tick(Minecraft minecraft, long nowNanos) {
        Objects.requireNonNull(minecraft);
        if (!config.replaceVanillaF2 || config.f2Behavior != F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER) {
            if (armed) gesture.reset();
            armed = false;
            return;
        }
        if (!armed) return;
        F2GestureMachine.Action action = gesture.update(minecraft.options.keyScreenshot.isDown(), nowNanos, thresholdNanos());
        if (action == F2GestureMachine.Action.TAP) {
            armed = false;
            instantPhoto.run();
        } else if (action == F2GestureMachine.Action.HOLD) {
            toggleViewfinder.run();
        }
    }

    public void reset() {
        armed = false;
        gesture.reset();
    }

    private long thresholdNanos() {
        return Math.multiplyExact((long) config.f2HoldThresholdMs, 1_000_000L);
    }
}
