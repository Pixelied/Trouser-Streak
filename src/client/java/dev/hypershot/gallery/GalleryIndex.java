package dev.hypershot.gallery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hypershot.util.AtomicJson;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Atomic, versioned gallery index. Full-resolution images are never decoded while browsing. */
public final class GalleryIndex {
    private static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path path;
    private final Logger logger;
    private final List<CaptureRecord> records = new ArrayList<>();

    public GalleryIndex(Path path, Logger logger) {
        this.path = Objects.requireNonNull(path);
        this.logger = Objects.requireNonNull(logger);
        load();
    }

    public synchronized List<CaptureRecord> all() {
        return records.stream().sorted(Comparator.comparingLong((CaptureRecord r) -> r.timestampEpochMillis).reversed()).toList();
    }

    public synchronized List<CaptureRecord> search(String query, boolean favoritesOnly) {
        String needle = Objects.requireNonNullElse(query, "").strip().toLowerCase(Locale.ROOT);
        return records.stream()
                .filter(r -> !favoritesOnly || r.favorite)
                .filter(r -> needle.isEmpty() || searchable(r).contains(needle))
                .sorted(Comparator.comparingLong((CaptureRecord r) -> r.timestampEpochMillis).reversed())
                .toList();
    }

    public synchronized Optional<CaptureRecord> find(String id) {
        return records.stream().filter(r -> Objects.equals(r.id, id)).findFirst();
    }

    public synchronized void add(CaptureRecord record) {
        records.removeIf(r -> Objects.equals(r.id, record.id) || Objects.equals(r.imagePath, record.imagePath));
        records.add(record);
        save();
    }

    public synchronized void setFavorite(String id, boolean value) {
        find(id).ifPresent(record -> record.favorite = value);
        save();
    }

    public synchronized void setRating(String id, int rating) {
        find(id).ifPresent(record -> record.rating = Math.max(0, Math.min(5, rating)));
        save();
    }

    public synchronized void setTags(String id, List<String> tags) {
        find(id).ifPresent(record -> record.tags = tags.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList());
        save();
    }

    public synchronized Optional<CaptureRecord> remove(String id) {
        Optional<CaptureRecord> found = find(id);
        found.ifPresent(records::remove);
        save();
        return found;
    }

    public CompletableFuture<Void> reconcileAsync(Path capturesDirectory, Executor executor, Consumer<Integer> completion) {
        return CompletableFuture.supplyAsync(() -> reconcile(capturesDirectory), executor)
                .thenAccept(completion);
    }

    private synchronized int reconcile(Path capturesDirectory) {
        int changed = 0;
        for (CaptureRecord record : records) {
            boolean missing = !Files.isRegularFile(record.image());
            if (record.missing != missing) { record.missing = missing; changed++; }
            if (!missing) {
                try {
                    long size = Files.size(record.image());
                    if (record.fileSize != size) { record.fileSize = size; changed++; }
                } catch (IOException ignored) {}
            }
        }
        if (changed > 0) save();
        return changed;
    }

    private void load() {
        if (!Files.exists(path)) return;
        try {
            State state = GSON.fromJson(Files.readString(path), State.class);
            if (state == null || state.schemaVersion != SCHEMA || state.records == null) throw new IOException("Unsupported gallery index schema");
            records.clear();
            for (CaptureRecord record : state.records) {
                if (record != null && record.id != null && record.imagePath != null) records.add(record);
            }
        } catch (Exception error) {
            logger.error("Unable to read HyperShot gallery index; preserving it and starting clean", error);
            try { Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignored) {}
            records.clear();
        }
    }

    private synchronized void save() {
        try { AtomicJson.write(path, GSON.toJson(new State(SCHEMA, new ArrayList<>(records)))); }
        catch (IOException error) { logger.error("Unable to save HyperShot gallery index", error); }
    }

    private static String searchable(CaptureRecord r) {
        return (Objects.requireNonNullElse(r.filename, "") + " " + Objects.requireNonNullElse(r.preset, "") + " "
                + Objects.requireNonNullElse(r.captureMode, "") + " " + Objects.requireNonNullElse(r.format, "") + " "
                + r.width + "x" + r.height + " " + String.join(" ", Objects.requireNonNullElse(r.tags, List.of())))
                .toLowerCase(Locale.ROOT);
    }

    private record State(int schemaVersion, List<CaptureRecord> records) {}
}
