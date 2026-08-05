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
    private DiagnosticsSnapshot snapshot;
    private String status = "";

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("HyperShot Diagnostics"), parent);
        snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths());
    }

    @Override
    protected void init() {
        super.init();
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> { snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths()); status = "Refreshed"; })
                .bounds(center - 154, this.height - 32, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Export"), b -> export()).bounds(center - 50, this.height - 32, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(center + 54, this.height - 32, 96, 20).build());
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
        int x = Math.max(24, this.width / 2 - 270);
        int y = 52;
        line(graphics, x, y, "Backend", snapshot.backend()); y += 18;
        line(graphics, x, y, "GPU", snapshot.gpu()); y += 18;
        line(graphics, x, y, "Vendor", snapshot.vendor()); y += 18;
        line(graphics, x, y, "Driver", snapshot.driver()); y += 18;
        line(graphics, x, y, "Maximum texture", snapshot.maximumTextureSize() + " px"); y += 18;
        line(graphics, x, y, "Heap used / max", humanBytes(snapshot.heapUsed()) + " / " + humanBytes(snapshot.heapMaximum())); y += 18;
        line(graphics, x, y, "Output usable space", snapshot.outputUsableBytes() < 0 ? "Unknown" : humanBytes(snapshot.outputUsableBytes())); y += 18;
        line(graphics, x, y, "Output writable", snapshot.outputWritable() ? "Yes" : "No"); y += 18;
        line(graphics, x, y, "Java", snapshot.javaVersion()); y += 18;
        line(graphics, x, y, "OS", snapshot.operatingSystem()); y += 18;
        line(graphics, x, y, "Captured", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(snapshot.capturedAt()));
        if (!status.isBlank()) graphics.centeredText(this.font, status, this.width / 2, this.height - 54, 0xFF8ED6A4);
    }

    private void line(GuiGraphicsExtractor graphics, int x, int y, String label, String value) {
        graphics.text(this.font, label, x, y, 0xFF8E97A6, false);
        graphics.text(this.font, value == null ? "Unknown" : value, x + 150, y, 0xFFFFFFFF, false);
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
