package dev.hypershot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.hypershot.core.OutputFormat;
import dev.hypershot.core.camera.CameraMode;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
import dev.hypershot.core.camera.F2Behavior;
import dev.hypershot.core.camera.GuideType;
import dev.hypershot.core.camera.ShaderSettleProfile;
import dev.hypershot.util.AtomicJson;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HyperShotConfig {
    public static final int CURRENT_SCHEMA = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int schemaVersion = CURRENT_SCHEMA;
    public String activePresetId = "vanilla-plus";
    public boolean replaceVanillaF2 = true;
    public boolean notificationsEnabled = true;
    public boolean showMenuButton = true;
    public int notificationSeconds = 8;
    public long freeDiskMarginBytes = 2L * 1024 * 1024 * 1024;
    public MetadataPrivacy metadataPrivacy = MetadataPrivacy.NORMAL;
    public List<CapturePreset> presets = builtIns();

    public F2Behavior f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
    public int f2HoldThresholdMs = 350;
    public CameraMode cameraMode = CameraMode.PHOTO;
    public int timerSeconds = 0;
    public GuideType guideType = GuideType.RULE_OF_THIRDS;
    public float guideOpacity = 0.45f;
    public ShaderSettleProfile shaderSettleProfile = ShaderSettleProfile.STANDARD;
    public int customShaderSettleMs = 1_000;
    public boolean cameraOverlayFade = true;
    public boolean countdownSounds = true;

    public int burstFrameCount = 5;
    public long burstIntervalMs = 250;
    public boolean timeUseCuratedSequence = true;
    public long timeStartTick = 23_000;
    public long timeEndTick = 1_000;
    public long timeStepTicks = 500;
    public boolean timeWrapDayBoundary = true;
    public boolean timeLockWeather = true;
    public boolean timeSettleBetweenFrames = true;

    public boolean cinematicWorldFreeze = false;
    public CinematicTimePreset cinematicTimePreset = CinematicTimePreset.CURRENT;
    public CinematicWeatherPreset cinematicWeatherPreset = CinematicWeatherPreset.CURRENT;
    public boolean cinematicWaitForChunks = true;
    public int cinematicChunkTimeoutMs = 10_000;
    public boolean cinematicCameraLock = true;
    public boolean cinematicFovLock = true;
    public boolean cinematicCleanFrame = true;

    public static HyperShotConfig load(Path path, Logger logger) {
        Objects.requireNonNull(path);
        if (!Files.exists(path)) {
            HyperShotConfig defaults = new HyperShotConfig();
            defaults.save(path, logger);
            return defaults;
        }
        try {
            HyperShotConfig config = GSON.fromJson(Files.readString(path), HyperShotConfig.class);
            if (config == null) throw new JsonParseException("Configuration is empty");
            config.migrateAndValidate();
            return config;
        } catch (Exception error) {
            logger.error("Unable to load HyperShot config; restoring defaults", error);
            try {
                Path malformed = path.resolveSibling(path.getFileName() + ".malformed");
                Files.move(path, malformed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                logger.warn("Unable to preserve malformed config", moveError);
            }
            HyperShotConfig defaults = new HyperShotConfig();
            defaults.save(path, logger);
            return defaults;
        }
    }

    public synchronized void save(Path path, Logger logger) {
        try {
            migrateAndValidate();
            AtomicJson.write(path, GSON.toJson(this));
        } catch (Exception error) {
            logger.error("Unable to save HyperShot config", error);
        }
    }

    public CapturePreset activePreset() {
        return presets.stream().filter(p -> p.id.equals(activePresetId)).findFirst().orElseGet(() -> presets.getFirst());
    }

    public CapturePreset preset(String id) {
        return presets.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + id));
    }

    private void migrateAndValidate() {
        if (schemaVersion <= 0) schemaVersion = 1;
        if (schemaVersion > CURRENT_SCHEMA) throw new IllegalArgumentException("Config was created by a newer HyperShot version");
        if (schemaVersion < 2) showMenuButton = true;
        boolean migrateUhdPresets = schemaVersion < 3;
        boolean migrateCameraSettings = schemaVersion < 4;
        boolean migrateSequenceSettings = schemaVersion < 5;
        boolean migrateCinematicSettings = schemaVersion < 6;
        if (migrateCameraSettings) restoreCameraDefaults();
        if (migrateSequenceSettings) restoreSequenceDefaults();
        if (migrateCinematicSettings) restoreCinematicDefaults();
        schemaVersion = CURRENT_SCHEMA;
        if (presets == null || presets.isEmpty()) presets = builtIns();
        if (migrateUhdPresets) migrateUhdBuiltIns();
        List<CapturePreset> sanitized = new ArrayList<>();
        for (CapturePreset preset : presets) {
            if (preset == null) continue;
            preset.validate();
            if (sanitized.stream().noneMatch(existing -> existing.id.equals(preset.id))) sanitized.add(preset);
        }
        if (sanitized.isEmpty()) sanitized = builtIns();
        presets = sanitized;
        if (presets.stream().noneMatch(p -> p.id.equals(activePresetId))) activePresetId = presets.getFirst().id;
        notificationSeconds = Math.max(2, Math.min(60, notificationSeconds));
        freeDiskMarginBytes = Math.max(256L * 1024 * 1024, freeDiskMarginBytes);
        if (metadataPrivacy == null) metadataPrivacy = MetadataPrivacy.NORMAL;
        validateCameraSettings();
        validateSequenceSettings();
        validateCinematicSettings();
    }

    private void restoreCameraDefaults() {
        f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
        f2HoldThresholdMs = 350;
        cameraMode = CameraMode.PHOTO;
        timerSeconds = 0;
        guideType = GuideType.RULE_OF_THIRDS;
        guideOpacity = 0.45f;
        shaderSettleProfile = ShaderSettleProfile.STANDARD;
        customShaderSettleMs = 1_000;
        cameraOverlayFade = true;
        countdownSounds = true;
    }

    private void restoreSequenceDefaults() {
        burstFrameCount = 5;
        burstIntervalMs = 250;
        timeUseCuratedSequence = true;
        timeStartTick = 23_000;
        timeEndTick = 1_000;
        timeStepTicks = 500;
        timeWrapDayBoundary = true;
        timeLockWeather = true;
        timeSettleBetweenFrames = true;
    }

    private void restoreCinematicDefaults() {
        cinematicWorldFreeze = false;
        cinematicTimePreset = CinematicTimePreset.CURRENT;
        cinematicWeatherPreset = CinematicWeatherPreset.CURRENT;
        cinematicWaitForChunks = true;
        cinematicChunkTimeoutMs = 10_000;
        cinematicCameraLock = true;
        cinematicFovLock = true;
        cinematicCleanFrame = true;
    }

    private void validateCameraSettings() {
        if (f2Behavior == null) f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
        if (cameraMode == null) cameraMode = CameraMode.PHOTO;
        if (guideType == null) guideType = GuideType.RULE_OF_THIRDS;
        if (shaderSettleProfile == null) shaderSettleProfile = ShaderSettleProfile.STANDARD;
        f2HoldThresholdMs = Math.max(150, Math.min(1_000, f2HoldThresholdMs));
        timerSeconds = Math.max(0, Math.min(60, timerSeconds));
        guideOpacity = Math.max(0.10f, Math.min(1.0f, guideOpacity));
        customShaderSettleMs = Math.max(0, Math.min(10_000, customShaderSettleMs));
    }

    private void validateSequenceSettings() {
        burstFrameCount = Math.max(1, Math.min(100, burstFrameCount));
        burstIntervalMs = Math.max(0, Math.min(60_000, burstIntervalMs));
        timeStartTick = Math.floorMod(timeStartTick, 24_000L);
        timeEndTick = Math.floorMod(timeEndTick, 24_000L);
        timeStepTicks = Math.max(1, Math.min(24_000L, timeStepTicks));
    }

    private void validateCinematicSettings() {
        if (cinematicTimePreset == null) cinematicTimePreset = CinematicTimePreset.CURRENT;
        if (cinematicWeatherPreset == null) cinematicWeatherPreset = CinematicWeatherPreset.CURRENT;
        cinematicChunkTimeoutMs = Math.max(1_000, Math.min(120_000, cinematicChunkTimeoutMs));
    }

    private void migrateUhdBuiltIns() {
        for (CapturePreset preset : presets) {
            if (preset == null || preset.id == null) continue;
            if (preset.id.equals("vanilla-plus")) preset.name = "Native";
            if (preset.id.equals("16k-square")) preset.name = "16,384 Square (Legacy)";
            if (preset.id.equals("20k-square")) preset.name = "20,000 Square (Legacy)";
        }
        for (CapturePreset builtIn : builtIns()) {
            if (presets.stream().noneMatch(existing -> existing != null && builtIn.id.equals(existing.id))) presets.add(builtIn);
        }
    }

    public static List<CapturePreset> builtIns() {
        List<CapturePreset> result = new ArrayList<>();
        result.add(new CapturePreset("vanilla-plus", "Native", CaptureMode.NATIVE, 0, 0, 2048, 0, 6, true, true, true, true));
        result.add(new CapturePreset("4k", "4K UHD", CaptureMode.SCALED, 3840, 2160, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("8k", "8K UHD", CaptureMode.TILED, 7680, 4320, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("16k", "16K UHD", CaptureMode.TILED, 15360, 8640, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("32k", "32K UHD — Experimental", CaptureMode.TILED, 30720, 17280, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("custom", "Custom", CaptureMode.TILED, 3840, 2160, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("phone", "Phone Wallpaper", CaptureMode.TILED, 2160, 3840, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("wallpaper", "5K Wallpaper", CaptureMode.TILED, 5120, 2880, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("social-jpeg", "Social Media JPEG", CaptureMode.SCALED, 2560, 1440, 2048, 16, 6,
                OutputFormat.JPEG, 0.92f, true, true, true, true));
        return result;
    }
}
