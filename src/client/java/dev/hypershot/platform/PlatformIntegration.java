package dev.hypershot.platform;

import net.minecraft.util.Util;

import java.nio.file.Path;

public final class PlatformIntegration {
    public void open(Path path) {
        Util.getPlatform().openFile(path.toFile());
    }

    public void reveal(Path path) {
        Path parent = path.toAbsolutePath().getParent();
        Util.getPlatform().openFile((parent == null ? path : parent).toFile());
    }
}
