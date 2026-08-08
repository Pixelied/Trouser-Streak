package dev.hypershot.core;

public record ProjectionWindow(double left, double right, double bottom, double top, double near, double far) {
    public ProjectionWindow {
        if (!(left < right) || !(bottom < top) || !(near > 0.0) || !(far > near)) {
            throw new IllegalArgumentException("Invalid projection window");
        }
    }
}
