package dev.hypershot.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

public final class AtomicOutput {
    private AtomicOutput() {}

    public static void write(Path target, IoPathConsumer writer) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(writer, "writer");
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = absolute.resolveSibling("." + absolute.getFileName() + "." + UUID.randomUUID() + ".part");
        boolean committed = false;
        try {
            writer.accept(temporary);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute);
            }
            committed = true;
        } finally {
            if (!committed) Files.deleteIfExists(temporary);
        }
    }

    public static Path uniqueSibling(Path requested) {
        if (!Files.exists(requested)) return requested;
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            Path candidate = requested.resolveSibling(stem + " (" + i + ")" + extension);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to create unique filename");
    }

    @FunctionalInterface public interface IoPathConsumer { void accept(Path path) throws IOException; }
}
