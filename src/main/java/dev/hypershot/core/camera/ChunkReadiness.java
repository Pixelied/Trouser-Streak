package dev.hypershot.core.camera;

public record ChunkReadiness(int loadedChunks, int totalChunks, boolean timedOut) {
    public ChunkReadiness {
        if (totalChunks < 1 || loadedChunks < 0 || loadedChunks > totalChunks) throw new IllegalArgumentException("Invalid chunk readiness");
    }

    public boolean complete() {
        return loadedChunks == totalChunks;
    }

    public int percent() {
        return (int) Math.round(loadedChunks * 100.0 / totalChunks);
    }
}
