package dev.hypershot.core;

import java.time.Duration;

public record CaptureProgressSnapshot(CapturePhase phase, int tilesCompleted, int totalTiles,
                                      long pixelsCompleted, long totalPixels, double pixelsFraction,
                                      Duration elapsed, Duration eta, double pixelsPerSecond) {}
