package dev.hypershot.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Objects;

/**
 * Render-thread-only scope that temporarily substitutes HyperShot's private render target
 * at GameRenderer's target lookup boundary without mutating Minecraft-owned final fields.
 */
public final class CaptureRenderContext {
    private static long nextGeneration;
    private static long activeGeneration;
    private static String activeSessionId;
    private static RenderTarget activeTarget;

    private CaptureRenderContext() {}

    public static Scope enter(String sessionId, RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(target, "target");
        if (activeTarget != null) {
            throw new IllegalStateException("Nested HyperShot capture render scope for " + activeSessionId);
        }
        long generation = ++nextGeneration;
        activeGeneration = generation;
        activeSessionId = sessionId;
        activeTarget = target;
        return new Scope(generation);
    }

    public static RenderTarget currentTarget() {
        return activeTarget;
    }

    public static boolean isActive() {
        return activeTarget != null;
    }

    public static final class Scope implements AutoCloseable {
        private final long generation;
        private boolean closed;

        private Scope(long generation) {
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            RenderSystem.assertOnRenderThread();
            if (activeGeneration != generation) return;
            activeTarget = null;
            activeSessionId = null;
            activeGeneration = 0;
        }
    }
}
