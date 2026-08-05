package dev.hypershot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.hypershot.util.AtomicJson;
import dev.hypershot.core.OutputFormat;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HyperShotConfig {
    public static final int CURRENT_SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int schemaVersion = CURRENT_SCHEMA;
    public String activePresetId = "vanilla-plus";
    public boolean replaceVanillaF2 = true;
    public boolean notificationsEnabled = true;
    public int notificationSeconds = 8;
    public long freeDiskMarginBytes = 2L * 1024 * 1024 * 1024;
    public MetadataPrivacy metadataPrivacy = MetadataPrivacy.NORMAL;
    public List<CapturePreset> presets = builtIns();

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
        if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA;
        if (schemaVersion > CURRENT_SCHEMA) throw new IllegalArgumentException("Config was created by a newer HyperShot version");
        schemaVersion = CURRENT_SCHEMA;
        if (presets == null || presets.isEmpty()) presets = builtIns();
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
    }

    public static List<CapturePreset> builtIns() {
        List<CapturePreset> result = new ArrayList<>();
        result.add(new CapturePreset("vanilla-plus", "Vanilla+", CaptureMode.NATIVE, 0, 0, 2048, 0, 6, true, true, true, true));
        result.add(new CapturePreset("clean", "Clean Screenshot", CaptureMode.NATIVE, 0, 0, 2048, 0, 6, true, true, true, true));
        result.add(new CapturePreset("4k", "4K", CaptureMode.SCALED, 3840, 2160, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("8k", "8K", CaptureMode.TILED, 7680, 4320, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("10k-square", "10K Square", CaptureMode.TILED, 10000, 10000, 2048, 32, 7, true, true, true, true));
        result.add(new CapturePreset("16k-square", "16K Square", CaptureMode.TILED, 16384, 16384, 2048, 32, 7, true, true, true, true));
        result.add(new CapturePreset("20k-square", "20K Square", CaptureMode.TILED, 20000, 20000, 2048, 32, 7, true, true, true, true));
        result.add(new CapturePreset("phone", "Phone Wallpaper", CaptureMode.TILED, 2160, 3840, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("wallpaper", "Wallpaper", CaptureMode.TILED, 5120, 2880, 2048, 32, 6, true, true, true, true));
        result.add(new CapturePreset("social-jpeg", "Social Media JPEG", CaptureMode.SCALED, 2560, 1440, 2048, 16, 6,
                OutputFormat.JPEG, 0.92f, true, true, true, true));
        return result;
    }
}
