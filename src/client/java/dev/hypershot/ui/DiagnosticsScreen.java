package dev.hypershot.ui;

import com.google.gson.GsonBuilder;
import dev.hypershot.HyperShotClient;
import dev.hypershot.diagnostics.DiagnosticsSnapshot;
import dev.hypershot.util.AtomicJson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DiagnosticsScreen extends HyperShotScreen {
    private static final int GAP = 4;
    private DiagnosticsSnapshot snapshot;
    private String status = "";

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("HyperShot Diagnostics"), parent);
        snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths());
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(520, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int buttonWidth = Math.max(48, (contentWidth - GAP * 2) / 3);
        int y = this.height - 30;
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths());
            status = "Refreshed";
        }).bounds(left, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Export"), b -> export())
                .bounds(left + buttonWidth + GAP, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + (buttonWidth + GAP) * 2, y, buttonWidth, 20).build());
    }

    private void export() {
        try {
            Path output = HyperShotClient.paths().logs().resolve("diagnostics-" + System.currentTimeMillis() + ".json");
            AtomicJson.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
            status = "Exported " + output.getFileName();
        } catch (Exception error) {
            status = "Export failed: " + error.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        int contentWidth = Math.min(520, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int columnGap = 12;
        int columnWidth = Math.max(70, (contentWidth - columnGap) / 2);
        String[][] metrics = {
                {"Backend", snapshot.backend()},
                {"GPU", snapshot.gpu()},
                {"Vendor", snapshot.vendor()},
                {"Driver", snapshot.driver()},
                {"Maximum texture", snapshot.maximumTextureSize() + " px"},
                {"Heap used / max", humanBytes(snapshot.heapUsed()) + " / " + humanBytes(snapshot.heapMaximum())},
                {"Output usable", snapshot.outputUsableBytes() < 0 ? "Unknown" : humanBytes(snapshot.outputUsableBytes())},
                {"Output writable", snapshot.outputWritable() ? "Yes" : "No"},
                {"Java", snapshot.javaVersion()},
                {"OS", snapshot.operatingSystem()},
                {"Captured", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(snapshot.capturedAt())}
        };

        for (int index = 0; index < metrics.length; index++) {
            int column = index % 2;
            int row = index / 2;
            int x = left + column * (columnWidth + columnGap);
            int y = 46 + row * 24;
            metric(graphics, x, y, columnWidth, metrics[index][0], metrics[index][1]);
        }
        if (!status.isBlank()) {
            graphics.centeredText(this.font, truncate(status, Math.max(12, (this.width - 24) / 6)),
                    this.width / 2, this.height - 44, 0xFF8ED6A4);
        }
    }

    private void metric(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value) {
        graphics.text(this.font, label, x, y, 0xFF8E97A6, false);
        graphics.text(this.font, truncate(value == null ? "Unknown" : value, Math.max(8, width / 6)),
                x, y + 11, 0xFFFFFFFF, true);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
