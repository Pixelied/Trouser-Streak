package dev.hypershot.gallery;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Small LRU cache. Only pre-generated thumbnails are decoded, never full captures. */
public final class ThumbnailTextureCache implements AutoCloseable {
    private final Minecraft minecraft;
    private final Logger logger;
    private final Executor ioExecutor;
    private final int maximumEntries;
    private final Map<Path, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);

    public ThumbnailTextureCache(Minecraft minecraft, Logger logger, Executor ioExecutor, int maximumEntries) {
        this.minecraft = Objects.requireNonNull(minecraft);
        this.logger = Objects.requireNonNull(logger);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.maximumEntries = Math.max(8, maximumEntries);
    }

    public synchronized Identifier get(Path path) {
        if (path == null) return null;
        Path key = path.toAbsolutePath().normalize();
        Entry entry = entries.get(key);
        if (entry != null) return entry.textureId;
        Entry loading = new Entry(null, true);
        entries.put(key, loading);
        ioExecutor.execute(() -> load(key));
        trim();
        return null;
    }

    private void load(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            minecraft.execute(() -> {
                Identifier id = Identifier.fromNamespaceAndPath("hypershot", "thumbnail/" + Integer.toUnsignedString(path.toString().hashCode(), 16));
                DynamicTexture texture = new DynamicTexture(() -> "HyperShot thumbnail " + path.getFileName(), image);
                minecraft.getTextureManager().register(id, texture);
                synchronized (ThumbnailTextureCache.this) {
                    Entry current = entries.get(path);
                    if (current == null) {
                        minecraft.getTextureManager().release(id);
                    } else {
                        entries.put(path, new Entry(id, false));
                        trim();
                    }
                }
            });
        } catch (Exception error) {
            logger.warn("Unable to load HyperShot thumbnail {}", path, error);
            synchronized (this) { entries.remove(path); }
        }
    }

    private void trim() {
        while (entries.size() > maximumEntries) {
            Path eldest = entries.keySet().iterator().next();
            Entry removed = entries.remove(eldest);
            if (removed != null && removed.textureId != null) minecraft.getTextureManager().release(removed.textureId);
        }
    }

    @Override
    public synchronized void close() {
        for (Entry entry : entries.values()) if (entry.textureId != null) minecraft.getTextureManager().release(entry.textureId);
        entries.clear();
    }

    private record Entry(Identifier textureId, boolean loading) {}
}
