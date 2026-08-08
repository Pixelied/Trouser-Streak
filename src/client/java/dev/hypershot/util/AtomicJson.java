package dev.hypershot.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicJson {
    private AtomicJson() {}

    public static void write(Path path, String json) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Path backup = absolute.resolveSibling(absolute.getFileName() + ".bak");
        Files.writeString(temporary, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        if (Files.exists(absolute)) Files.copy(absolute, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
