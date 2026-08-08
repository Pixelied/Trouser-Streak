package dev.hypershot.core;

public final class TileProjectionCalculator {
    private TileProjectionCalculator() {}

    public static ProjectionWindow fullFrame(int width, int height, double verticalFovDegrees, double near, double far) {
        validate(width, height, verticalFovDegrees, near, far);
        double top = Math.tan(Math.toRadians(verticalFovDegrees) * 0.5) * near;
        double right = top * ((double) width / height);
        return new ProjectionWindow(-right, right, -top, top, near, far);
    }

    public static ProjectionWindow window(Tile tile, int outputWidth, int outputHeight,
                                          double verticalFovDegrees, double near, double far) {
        ProjectionWindow full = fullFrame(outputWidth, outputHeight, verticalFovDegrees, near, far);
        double x0 = tile.renderX() / (double) outputWidth;
        double x1 = (tile.renderX() + tile.renderWidth()) / (double) outputWidth;
        double y0 = tile.renderY() / (double) outputHeight;
        double y1 = (tile.renderY() + tile.renderHeight()) / (double) outputHeight;
        double left = lerp(full.left(), full.right(), x0);
        double right = lerp(full.left(), full.right(), x1);
        // Image coordinates grow downward; frustum coordinates grow upward.
        double top = lerp(full.top(), full.bottom(), y0);
        double bottom = lerp(full.top(), full.bottom(), y1);
        return new ProjectionWindow(left, right, bottom, top, near, far);
    }

    private static void validate(int width, int height, double fov, double near, double far) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("dimensions");
        if (!(fov > 0.0 && fov < 179.0)) throw new IllegalArgumentException("fov");
        if (!(near > 0.0 && far > near)) throw new IllegalArgumentException("clip planes");
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
