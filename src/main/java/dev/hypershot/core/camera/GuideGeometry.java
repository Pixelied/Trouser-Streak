package dev.hypershot.core.camera;

import java.util.List;

public final class GuideGeometry {
    private static final double GOLDEN_LOW = 0.382;
    private static final double GOLDEN_HIGH = 0.618;

    private GuideGeometry() {}

    public static List<Line> lines(GuideType type, int width, int height) {
        if (type == null) throw new IllegalArgumentException("Guide type is required");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Guide viewport must be positive");
        return switch (type) {
            case OFF -> List.of();
            case RULE_OF_THIRDS -> List.of(
                    new Line(width / 3, 0, width / 3, height),
                    new Line(width * 2 / 3, 0, width * 2 / 3, height),
                    new Line(0, height / 3, width, height / 3),
                    new Line(0, height * 2 / 3, width, height * 2 / 3));
            case CENTER_CROSS -> List.of(
                    new Line(width / 2, 0, width / 2, height),
                    new Line(0, height / 2, width, height / 2));
            case GOLDEN_RATIO -> {
                int x1 = (int) Math.round(width * GOLDEN_LOW);
                int x2 = (int) Math.round(width * GOLDEN_HIGH);
                int y1 = (int) Math.round(height * GOLDEN_LOW);
                int y2 = (int) Math.round(height * GOLDEN_HIGH);
                yield List.of(
                        new Line(x1, 0, x1, height),
                        new Line(x2, 0, x2, height),
                        new Line(0, y1, width, y1),
                        new Line(0, y2, width, y2));
            }
            case HORIZON -> List.of(new Line(0, height / 2, width, height / 2));
            case DIAGONAL -> List.of(
                    new Line(0, 0, width, height),
                    new Line(width, 0, 0, height));
            case SAFE_FRAME -> {
                int insetX = Math.max(1, (int) Math.round(width * 0.05));
                int insetY = Math.max(1, (int) Math.round(height * 0.05));
                int right = width - insetX;
                int bottom = height - insetY;
                yield List.of(
                        new Line(insetX, insetY, right, insetY),
                        new Line(right, insetY, right, bottom),
                        new Line(right, bottom, insetX, bottom),
                        new Line(insetX, bottom, insetX, insetY));
            }
        };
    }

    public record Line(int x1, int y1, int x2, int y2) {}
}
