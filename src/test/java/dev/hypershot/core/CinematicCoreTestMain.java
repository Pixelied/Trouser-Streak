package dev.hypershot.core;

import dev.hypershot.core.camera.CinematicCapabilities;
import dev.hypershot.core.camera.CinematicOptions;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
import dev.hypershot.core.camera.SceneRestoreState;

import java.lang.reflect.Method;

public final class CinematicCoreTestMain {
    private static int assertions;

    public static void main(String[] args) {
        capabilitiesGateWorldControls();
        optionsValidate();
        restorationIsIdempotent();
        preSnapshotCancellationCanCleanUp();
        restoredLifecycleCanStartNewSession();
        System.out.println("HyperShot cinematic core tests: PASS (" + assertions + " assertions)");
    }

    private static void capabilitiesGateWorldControls() {
        CinematicCapabilities multiplayer = CinematicCapabilities.multiplayer();
        check(!multiplayer.worldFreeze(), "multiplayer cannot freeze server world");
        check(!multiplayer.authoritativeTime(), "multiplayer cannot control server time");
        check(!multiplayer.authoritativeWeather(), "multiplayer cannot control server weather");
        check(multiplayer.cameraLock(), "client camera lock works everywhere");
        check(multiplayer.fovLock(), "client fov lock works everywhere");
        check(multiplayer.chunkReadiness(), "client chunk readiness works everywhere");

        CinematicCapabilities singleplayer = CinematicCapabilities.singleplayer(true);
        check(singleplayer.worldFreeze(), "singleplayer world freeze available when vanilla tick freeze exists");
        check(singleplayer.authoritativeTime(), "singleplayer time control available");
        check(singleplayer.authoritativeWeather(), "singleplayer weather control available");
    }

    private static void optionsValidate() {
        CinematicOptions options = new CinematicOptions(true, CinematicTimePreset.GOLDEN_HOUR,
                CinematicWeatherPreset.CLEAR, true, 10_000, true, true, true);
        check(options.worldFreeze(), "freeze option preserved");
        eq(CinematicTimePreset.GOLDEN_HOUR, options.timePreset(), "time preset preserved");
        eq(CinematicWeatherPreset.CLEAR, options.weatherPreset(), "weather preset preserved");
        expectThrows(() -> new CinematicOptions(false, CinematicTimePreset.CURRENT, CinematicWeatherPreset.CURRENT,
                true, 0, true, true, true), "zero chunk timeout rejected");
        expectThrows(() -> new CinematicOptions(false, CinematicTimePreset.CURRENT, CinematicWeatherPreset.CURRENT,
                true, 120_001, true, true, true), "oversized chunk timeout rejected");
    }

    private static void restorationIsIdempotent() {
        SceneRestoreState state = new SceneRestoreState();
        eq(SceneRestoreState.Phase.NOT_CAPTURED, state.phase(), "restore begins uncaptured");
        check(state.markCaptured(), "snapshot captured once");
        check(!state.markCaptured(), "snapshot capture idempotent");
        check(state.beginRestore(), "restore starts once");
        check(!state.beginRestore(), "second restore start ignored");
        state.markRestored();
        eq(SceneRestoreState.Phase.RESTORED, state.phase(), "restore completes");
        check(!state.beginRestore(), "restored state cannot restart restoration");
    }

    private static void preSnapshotCancellationCanCleanUp() {
        SceneRestoreState state = new SceneRestoreState();
        check(state.beginRestore(), "cancel before async snapshot may still enter cleanup");
        check(!state.beginRestore(), "pre-snapshot cleanup remains idempotent");
        state.markRestored();
        eq(SceneRestoreState.Phase.RESTORED, state.phase(), "pre-snapshot cleanup reaches restored state");
    }

    private static void restoredLifecycleCanStartNewSession() {
        SceneRestoreState state = new SceneRestoreState();
        check(state.markCaptured(), "first session captures snapshot");
        check(state.beginRestore(), "first session begins restore");
        state.markRestored();
        eq(SceneRestoreState.Phase.RESTORED, state.phase(), "first session restored");
        try {
            Method beginSession = SceneRestoreState.class.getMethod("beginSession");
            check((Boolean) beginSession.invoke(state), "restored lifecycle accepts a new session");
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("SceneRestoreState needs beginSession() so a second Cinematic shot can restore", missing);
        }
        eq(SceneRestoreState.Phase.NOT_CAPTURED, state.phase(), "new session resets restoration lifecycle");
        check(state.markCaptured(), "second session can capture snapshot");
        check(state.beginRestore(), "second session can restore");
    }

    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
    }

    private static void expectThrows(Runnable runnable, String name) {
        assertions++;
        try {
            runnable.run();
            throw new AssertionError(name + ": expected exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
