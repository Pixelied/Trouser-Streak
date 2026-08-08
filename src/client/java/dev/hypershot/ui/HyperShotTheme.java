package dev.hypershot.ui;

import dev.hypershot.core.UiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared Minecraft-native visual language for every HyperShot screen. */
final class HyperShotTheme {
    static final int BACKGROUND_TOP = 0xFF111318;
    static final int BACKGROUND_BOTTOM = 0xFF181C23;
    static final int PANEL = 0xE6171A20;
    static final int PANEL_RAISED = 0xF01D2129;
    static final int PANEL_HOVER = 0xF0252A34;
    static final int BORDER = 0xFF3C4350;
    static final int BORDER_SOFT = 0xFF2D333D;
    static final int ACCENT = 0xFF6A7CFF;
    static final int ACCENT_BRIGHT = 0xFF8DA0FF;
    static final int TEXT = 0xFFF2F4F8;
    static final int TEXT_MUTED = 0xFF9AA3B1;
    static final int TEXT_DIM = 0xFF737D8C;
    static final int SUCCESS = 0xFF69D58D;
    static final int WARNING = 0xFFFFC857;
    static final int ERROR = 0xFFFF6B6B;

    private HyperShotTheme() {}

    static void background(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        for (int y = 0; y < height; y += 24) {
            graphics.fill(0, y, width, y + 1, 0x101A1E25);
        }
    }

    static void panel(GuiGraphicsExtractor graphics, UiLayout.Rect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        shadow(graphics, rect);
        graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), PANEL);
        graphics.outline(rect.left(), rect.top(), rect.width(), rect.height(), BORDER);
    }

    static void raisedPanel(GuiGraphicsExtractor graphics, UiLayout.Rect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        shadow(graphics, rect);
        graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), PANEL_RAISED);
        graphics.outline(rect.left(), rect.top(), rect.width(), rect.height(), BORDER);
    }

    static void shadow(GuiGraphicsExtractor graphics, UiLayout.Rect rect) {
        graphics.fill(rect.left() + 3, rect.top() + 3, rect.right() + 3, rect.bottom() + 3, 0x50000000);
    }

    static void accentBar(GuiGraphicsExtractor graphics, UiLayout.Rect rect) {
        graphics.fill(rect.left(), rect.top(), rect.left() + 3, rect.bottom(), ACCENT);
    }

    static void divider(GuiGraphicsExtractor graphics, int x1, int y, int x2) {
        graphics.fill(x1, y, x2, y + 1, BORDER_SOFT);
    }

    static void labelValue(GuiGraphicsExtractor graphics, Font font, String label, String value, int x, int y) {
        graphics.text(font, label, x, y, TEXT_MUTED, false);
        graphics.text(font, value, x, y + 13, TEXT, false);
    }

    static void sectionTitle(GuiGraphicsExtractor graphics, Font font, Component title, Component description,
                             UiLayout.Rect rect) {
        graphics.text(font, title, rect.left(), rect.top(), TEXT, true);
        if (description != null) {
            graphics.textWithWordWrap(font, description, rect.left(), rect.top() + 15, rect.width(), TEXT_MUTED, false);
        }
    }

    static String humanBytes(long bytes) {
        if (bytes < 0) return "Unknown";
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    static String titleCaseEnum(Enum<?> value) {
        return titleCase(value.name());
    }

    static String titleCase(String value) {
        String raw = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (upper && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
            }
            if (c == ' ') upper = true;
        }
        return result.toString();
    }
}
