package dev.hypershot.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record HyperShotPaths(Path root, Path captures, Path sessions, Path thumbnails, Path metadata,
                             Path temporary, Path recovery, Path presets, Path logs, Path trash, Path config, Path index) {
    public static HyperShotPaths create(Path gameDirectory) throws IOException {
        Path root = gameDirectory.resolve("screenshots").resolve("hypershot");
        HyperShotPaths paths = new HyperShotPaths(root, root.resolve("captures"), root.resolve("sessions"),
                root.resolve("thumbnails"), root.resolve("metadata"), root.resolve("temporary"),
                root.resolve("recovery"), root.resolve("presets"), root.resolve("logs"), root.resolve("trash"),
                gameDirectory.resolve("config").resolve("hypershot.json"), root.resolve("gallery-index.json"));
        Files.createDirectories(paths.captures);
        Files.createDirectories(paths.sessions);
        Files.createDirectories(paths.thumbnails);
        Files.createDirectories(paths.metadata);
        Files.createDirectories(paths.temporary);
        Files.createDirectories(paths.recovery);
        Files.createDirectories(paths.presets);
        Files.createDirectories(paths.logs);
        Files.createDirectories(paths.trash);
        return paths;
    }
}
