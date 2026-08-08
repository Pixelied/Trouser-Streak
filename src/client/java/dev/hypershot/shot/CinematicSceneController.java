package dev.hypershot.shot;

import dev.hypershot.core.camera.CinematicCapabilities;
import dev.hypershot.core.camera.CinematicOptions;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
import dev.hypershot.core.camera.SceneRestoreState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Integrated-server scene mutations owned by one Cinematic shot and restored idempotently. */
public final class CinematicSceneController {
    private static final long REPIN_INTERVAL_NANOS = 200_000_000L;

    private final TimeSceneController timeController;
    private final SceneRestoreState restoreState = new SceneRestoreState();
    private final AtomicBoolean serverSnapshotReady = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicReference<String> error = new AtomicReference<>();

    private MinecraftServer server;
    private CinematicOptions options;
    private WeatherSnapshot originalWeather;
    private boolean originalFrozen;
    private volatile boolean freezeApplied;
    private boolean timeControlActive;
    private boolean weatherControlActive;
    private long lastRepinNanos;

    public CinematicSceneController(TimeSceneController timeController) {
        this.timeController = Objects.requireNonNull(timeController);
    }

    public CinematicCapabilities capabilities(Minecraft minecraft) {
        if (minecraft != null && minecraft.level != null && minecraft.getSingleplayerServer() != null) {
            return CinematicCapabilities.singleplayer(true);
        }
        return CinematicCapabilities.multiplayer();
    }

    public boolean begin(Minecraft minecraft, CinematicOptions requested) {
        Objects.requireNonNull(minecraft);
        Objects.requireNonNull(requested);
        if (server != null) return false;
        MinecraftServer candidate = minecraft.getSingleplayerServer();
        if (candidate == null || minecraft.level == null) return false;
        if (!restoreState.beginSession()) return false;
        CinematicOptions gated = requested.gatedBy(capabilities(minecraft));

        timeControlActive = gated.timePreset() != CinematicTimePreset.CURRENT;
        weatherControlActive = gated.weatherPreset() != CinematicWeatherPreset.CURRENT;
        if (timeControlActive && !timeController.begin(minecraft)) return false;

        server = candidate;
        options = gated;
        originalWeather = null;
        originalFrozen = false;
        freezeApplied = false;
        lastRepinNanos = 0L;
        serverSnapshotReady.set(false);
        failed.set(false);
        error.set(null);
        try {
            candidate.execute(() -> {
                try {
                    WeatherData data = candidate.getWeatherData();
                    originalWeather = WeatherSnapshot.capture(data);
                    originalFrozen = candidate.tickRateManager().isFrozen();
                    serverSnapshotReady.set(true);
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
        return true;
    }

    public boolean snapshotReady() {
        boolean timeReady = !timeControlActive || timeController.snapshotReady();
        boolean ready = server != null && serverSnapshotReady.get() && timeReady && !failed.get();
        if (ready) restoreState.markCaptured();
        return ready;
    }

    /** Apply only the temporary time/weather values explicitly requested by the Cinematic preset. */
    public void applyScene() {
        if (!snapshotReady()) throw new IllegalStateException("Cinematic scene snapshot is not ready");
        if (timeControlActive) {
            Long requestedTime = options.timePreset().dayTime();
            if (requestedTime == null) throw new IllegalStateException("Active Cinematic time control has no target");
            timeController.applyTime(requestedTime);
        }
        if (weatherControlActive) scheduleWeatherPin();
    }

    public boolean clientObservedScene(Minecraft minecraft) {
        if (!snapshotReady()) return false;
        return !timeControlActive || timeController.clientObservedTarget(minecraft);
    }

    public void applyWorldFreeze() {
        if (!snapshotReady() || !options.worldFreeze() || freezeApplied) return;
        MinecraftServer current = server;
        try {
            current.execute(() -> {
                try {
                    current.tickRateManager().setFrozen(true);
                    freezeApplied = true;
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public boolean worldFreezeReady() {
        return options == null || !options.worldFreeze() || freezeApplied;
    }

    public void tick(Minecraft minecraft, long nowNanos) {
        if (server == null) return;
        if (timeControlActive) timeController.tick(minecraft, nowNanos);
        if (minecraft == null || minecraft.level == null || minecraft.getSingleplayerServer() != server) {
            restore();
            return;
        }
        if (weatherControlActive && snapshotReady() && nowNanos - lastRepinNanos >= REPIN_INTERVAL_NANOS) {
            scheduleWeatherPin();
            lastRepinNanos = nowNanos;
        }
    }

    public CinematicOptions options() { return options; }
    public boolean failed() { return failed.get() || (timeControlActive && timeController.failed()); }
    public String errorMessage() {
        return timeControlActive && timeController.failed()
                ? timeController.errorMessage()
                : Objects.requireNonNullElse(error.get(), "Cinematic scene control failed");
    }
    public boolean active() { return server != null; }
    public boolean restorationComplete() { return restoreState.phase() == SceneRestoreState.Phase.RESTORED; }

    public void restore() {
        MinecraftServer restoreServer = server;
        if (restoreServer == null) return;
        if (!restoreState.beginRestore()) return;

        boolean hadServerSnapshot = serverSnapshotReady.get();
        WeatherSnapshot weather = hadServerSnapshot ? originalWeather : null;
        boolean frozen = originalFrozen;
        boolean restoreTime = timeControlActive;
        if (restoreTime) timeController.restore();

        server = null;
        options = null;
        originalWeather = null;
        freezeApplied = false;
        timeControlActive = false;
        weatherControlActive = false;
        serverSnapshotReady.set(false);
        try {
            restoreServer.execute(() -> {
                try {
                    if (hadServerSnapshot) {
                        if (weather != null) weather.restore(restoreServer.getWeatherData());
                        restoreServer.tickRateManager().setFrozen(frozen);
                    }
                } catch (Throwable failure) {
                    fail(failure);
                } finally {
                    restoreState.markRestored();
                }
            });
        } catch (Throwable failure) {
            fail(failure);
            restoreState.markRestored();
        }
    }

    private void scheduleWeatherPin() {
        MinecraftServer current = server;
        CinematicOptions currentOptions = options;
        WeatherSnapshot baseline = originalWeather;
        if (current == null || currentOptions == null || baseline == null || !weatherControlActive) return;
        try {
            current.execute(() -> {
                try {
                    WeatherData data = current.getWeatherData();
                    WeatherSnapshot desired = switch (currentOptions.weatherPreset()) {
                        case CURRENT -> baseline;
                        case CLEAR -> WeatherSnapshot.clear();
                        case RAIN -> WeatherSnapshot.rain();
                        case THUNDER -> WeatherSnapshot.thunder();
                    };
                    desired.restore(data);
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void fail(Throwable failure) {
        failed.set(true);
        error.set(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }

    private record WeatherSnapshot(int clearTime, int rainTime, int thunderTime, boolean raining, boolean thundering) {
        static WeatherSnapshot capture(WeatherData data) {
            return new WeatherSnapshot(data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(), data.isRaining(), data.isThundering());
        }

        static WeatherSnapshot clear() { return new WeatherSnapshot(12_000, 0, 0, false, false); }
        static WeatherSnapshot rain() { return new WeatherSnapshot(0, 12_000, 0, true, false); }
        static WeatherSnapshot thunder() { return new WeatherSnapshot(0, 12_000, 12_000, true, true); }

        void restore(WeatherData data) {
            data.setClearWeatherTime(clearTime);
            data.setRainTime(rainTime);
            data.setThunderTime(thunderTime);
            data.setRaining(raining);
            data.setThundering(thundering);
        }
    }
}
