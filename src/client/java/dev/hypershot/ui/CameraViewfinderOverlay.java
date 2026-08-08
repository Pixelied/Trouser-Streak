package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.camera.GuideGeometry;
import dev.hypershot.core.camera.GuideType;
import dev.hypershot.core.camera.ShotReadiness;
import dev.hypershot.core.camera.ShotReadinessState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** Camera HUD that leaves normal mouselook/input intact while the player composes a shot. */
public final class CameraViewfinderOverlay {
    private final Minecraft minecraft;

    public CameraViewfinderOverlay(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics) {
        if (!HyperShotClient.isCameraViewfinderOpen()) return;
        if (HyperShotClient.captureManager().isRenderingCapturePass()) return;
        if (minecraft.level == null) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        drawGuides(graphics, width, height);

        CapturePreset preset = HyperShotClient.config().activePreset();
        int outputWidth = preset.width == 0 ? minecraft.gameRenderer.mainRenderTarget().width : preset.width;
        int outputHeight = preset.height == 0 ? minecraft.gameRenderer.mainRenderTarget().height : preset.height;
        ShotReadiness readiness = HyperShotClient.shotCoordinator().readiness(System.nanoTime());

        int margin = 8;
        int topWidth = Math.min(width - margin * 2, 300);
        graphics.fill(margin + 2, margin + 2, margin + topWidth + 2, margin + 42, 0x50000000);
        graphics.fill(margin, margin, margin + topWidth, margin + 40, 0xB0181B21);
        graphics.outline(margin, margin, topWidth, 40, 0xB05B6370);
        graphics.fill(margin, margin, margin + 3, margin + 40, readinessColor(readiness.state()));
        graphics.text(minecraft.font, "HYPERSHOT  •  PHOTO", margin + 10, margin + 8, HyperShotTheme.TEXT, true);
        graphics.text(minecraft.font, outputWidth + " × " + outputHeight + "  •  " + preset.outputFormat.name(),
                margin + 10, margin + 23, HyperShotTheme.TEXT_MUTED, false);

        String readinessText = readinessLabel(readiness);
        int readyWidth = minecraft.font.width(readinessText) + 20;
        int readyX = width - margin - readyWidth;
        graphics.fill(readyX + 2, margin + 2, width - margin + 2, margin + 24, 0x50000000);
        graphics.fill(readyX, margin, width - margin, margin + 22, 0xB0181B21);
        graphics.outline(readyX, margin, readyWidth, 22, readinessColor(readiness.state()));
        graphics.text(minecraft.font, readinessText, readyX + 10, margin + 7, readinessColor(readiness.state()), true);

        String timer = HyperShotClient.config().timerSeconds == 0 ? "Timer Off" : "Timer " + HyperShotClient.config().timerSeconds + "s";
        String settle = "Settle " + titleCase(HyperShotClient.config().shaderSettleProfile.name());
        String guide = HyperShotClient.config().guideType == GuideType.OFF ? "Guides Off" : titleCase(HyperShotClient.config().guideType.name());
        String controls = timer + "   •   " + settle + "   •   " + guide + "   •   F7 Controls   •   F2 Shutter";
        int footerWidth = Math.min(width - 16, minecraft.font.width(controls) + 20);
        int footerX = (width - footerWidth) / 2;
        int footerY = height - 30;
        graphics.fill(footerX + 2, footerY + 2, footerX + footerWidth + 2, footerY + 24, 0x50000000);
        graphics.fill(footerX, footerY, footerX + footerWidth, footerY + 22, 0xB0181B21);
        graphics.outline(footerX, footerY, footerWidth, 22, 0xA05B6370);
        String clipped = clipToWidth(controls, Math.max(1, footerWidth - 16));
        graphics.text(minecraft.font, clipped, footerX + 8, footerY + 7, HyperShotTheme.TEXT_MUTED, false);
    }

    private void drawGuides(GuiGraphicsExtractor graphics, int width, int height) {
        GuideType type = HyperShotClient.config().guideType;
        if (type == GuideType.OFF) return;
        int alpha = Math.max(0x20, Math.min(0xE6, Math.round(HyperShotClient.config().guideOpacity * 255.0f)));
        int color = (alpha << 24) | 0xE7EBF2;
        for (GuideGeometry.Line line : GuideGeometry.lines(type, width, height)) drawLine(graphics, line, color);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, GuideGeometry.Line line, int color) {
        if (line.y1() == line.y2()) {
            graphics.fill(Math.min(line.x1(), line.x2()), line.y1(), Math.max(line.x1(), line.x2()) + 1, line.y1() + 1, color);
            return;
        }
        if (line.x1() == line.x2()) {
            graphics.fill(line.x1(), Math.min(line.y1(), line.y2()), line.x1() + 1, Math.max(line.y1(), line.y2()) + 1, color);
            return;
        }
        int dx = line.x2() - line.x1();
        int dy = line.y2() - line.y1();
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            int x = line.x1() + (int) Math.round(dx * (i / (double) steps));
            int y = line.y1() + (int) Math.round(dy * (i / (double) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private String clipToWidth(String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && minecraft.font.width(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static String readinessLabel(ShotReadiness readiness) {
        if (readiness.remainingNanos() > 0) {
            double seconds = readiness.remainingNanos() / 1_000_000_000.0;
            if (readiness.state() == ShotReadinessState.WAITING_FOR_TIMER) {
                return "TIMER " + Math.max(1, (int) Math.ceil(seconds));
            }
            if (readiness.state() == ShotReadinessState.SETTLING_SHADERS) {
                return String.format(Locale.ROOT, "SETTLING %.1fs", seconds);
            }
        }
        return readiness.state().name().replace('_', ' ');
    }

    private static int readinessColor(ShotReadinessState state) {
        return switch (state) {
            case READY -> HyperShotTheme.SUCCESS;
            case WAITING_FOR_TIMER, SETTLING_SHADERS, HIGH_LOAD, FINALIZING -> HyperShotTheme.WARNING;
            case CAPTURING -> HyperShotTheme.ACCENT_BRIGHT;
            case BLOCKED, FAILED -> HyperShotTheme.ERROR;
            case CANCELLED -> HyperShotTheme.TEXT_MUTED;
        };
    }

    private static String titleCase(String value) {
        return HyperShotTheme.titleCase(value);
    }
}
