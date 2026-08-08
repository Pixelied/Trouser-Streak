package dev.hypershot.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure responsive layout calculation shared by HyperShot screens.
 * Keeps the Minecraft GUI usable from small GUI scales through ultrawide windows.
 */
public record UiLayout(Rect outer, Rect header, Rect sidebar, Rect content, Rect footer, boolean compact) {
    private static final int MARGIN = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int GAP = 8;
    private static final int SIDEBAR_WIDTH = 184;

    public UiLayout {
        if (outer == null || header == null || sidebar == null || content == null || footer == null) {
            throw new NullPointerException("layout rectangles");
        }
    }

    public static UiLayout compute(int width, int height, boolean requestSidebar) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("GUI dimensions must be positive");
        boolean compact = width < 700 || height < 320;
        int margin = Math.min(MARGIN, Math.max(4, Math.min(width, height) / 20));
        Rect outer = new Rect(margin, margin, Math.max(1, width - margin * 2), Math.max(1, height - margin * 2));
        Rect header = new Rect(outer.left(), outer.top(), outer.width(), Math.min(HEADER_HEIGHT, outer.height()));
        int footerHeight = Math.min(FOOTER_HEIGHT, Math.max(20, outer.height() / 5));
        Rect footer = new Rect(outer.left(), outer.bottom() - footerHeight, outer.width(), footerHeight);
        int bodyTop = header.bottom() + GAP;
        int bodyBottom = Math.max(bodyTop + 1, footer.top() - GAP);
        int bodyHeight = Math.max(1, bodyBottom - bodyTop);
        boolean sidebarVisible = requestSidebar && !compact && outer.width() >= SIDEBAR_WIDTH + 360 + GAP;
        Rect sidebar = sidebarVisible
                ? new Rect(outer.left(), bodyTop, SIDEBAR_WIDTH, bodyHeight)
                : new Rect(outer.left(), bodyTop, 0, bodyHeight);
        int contentLeft = sidebarVisible ? sidebar.right() + GAP : outer.left();
        Rect content = new Rect(contentLeft, bodyTop, Math.max(1, outer.right() - contentLeft), bodyHeight);
        return new UiLayout(outer, header, sidebar, content, footer, compact);
    }

    /** Distributes controls without overlap and gives leftover pixels to the first controls. */
    public static List<Rect> distribute(Rect row, int count, int requestedGap) {
        if (row == null) throw new NullPointerException("row");
        if (count < 1) throw new IllegalArgumentException("Control count must be positive");
        int gap = count == 1 ? 0 : Math.min(Math.max(0, requestedGap), row.width() / (count - 1));
        int usable = Math.max(0, row.width() - gap * (count - 1));
        int base = usable / count;
        int remainder = usable % count;
        int x = row.left();
        List<Rect> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int width = base + (i < remainder ? 1 : 0);
            result.add(new Rect(x, row.top(), width, row.height()));
            x += width + gap;
        }
        return List.copyOf(result);
    }

    public record Rect(int left, int top, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Negative rectangle size");
        }

        public int right() { return left + width; }
        public int bottom() { return top + height; }
        public int centerX() { return left + width / 2; }
        public int centerY() { return top + height / 2; }
        public Rect inset(int amount) {
            int safe = Math.max(0, amount);
            return new Rect(left + safe, top + safe, Math.max(0, width - safe * 2), Math.max(0, height - safe * 2));
        }
    }
}
