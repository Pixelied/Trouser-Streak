package dev.hypershot.ui;

import com.google.gson.GsonBuilder;
import dev.hypershot.HyperShotClient;
import dev.hypershot.core.UiLayout;
import dev.hypershot.diagnostics.DiagnosticsSnapshot;
import dev.hypershot.util.AtomicJson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

public final class DiagnosticsScreen extends HyperShotScreen {
    private DiagnosticsSnapshot snapshot;
    private String status = "";
    private boolean statusError;

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("HyperShot Diagnostics"), parent);
        snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths());
    }

    @Override
    protected void init() {
        super.init();
        UiLayout layout = layout(false);
        var actions = footerButtons(layout, 4);
        UiLayout.Rect refresh = actions.get(0);
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            snapshot = DiagnosticsSnapshot.capture(HyperShotClient.paths());
            status = "Diagnostics refreshed";
            statusError = false;
        }).bounds(refresh.left(), refresh.top(), refresh.width(), refresh.height()).build());
        UiLayout.Rect export = actions.get(1);
        this.addRenderableWidget(Button.builder(Component.literal(layout.compact() ? "Export" : "Export report"), b -> export())
                .bounds(export.left(), export.top(), export.width(), export.height()).build());
        UiLayout.Rect logs = actions.get(2);
        this.addRenderableWidget(Button.builder(Component.literal("Open logs"), b -> HyperShotClient.platform().open(HyperShotClient.paths().logs()))
                .bounds(logs.left(), logs.top(), logs.width(), logs.height()).build());
        UiLayout.Rect done = actions.get(3);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(done.left(), done.top(), done.width(), done.height()).build());
    }

    private void export() {
        try {
            Path output = HyperShotClient.paths().logs().resolve("diagnostics-" + System.currentTimeMillis() + ".json");
            AtomicJson.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
            status = "Exported " + output.getFileName();
            statusError = false;
        } catch (Exception error) {
            status = "Export failed: " + error.getMessage();
            statusError = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiLayout layout = layout(false);
        drawChrome(graphics, layout, Component.literal("Captured " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(snapshot.capturedAt())));
        drawContentPanels(graphics, layout);

        UiLayout.Rect inner = layout.content().inset(14);
        int gap = 10;
        boolean twoColumns = inner.width() >= 680;
        int cardWidth = twoColumns ? (inner.width() - gap) / 2 : inner.width();
        UiLayout.Rect renderer = new UiLayout.Rect(inner.left(), inner.top(), cardWidth, Math.min(172, inner.height()));
        UiLayout.Rect system = twoColumns
                ? new UiLayout.Rect(renderer.right() + gap, inner.top(), cardWidth, renderer.height())
                : new UiLayout.Rect(inner.left(), renderer.bottom() + gap, inner.width(), Math.min(150, Math.max(1, inner.bottom() - renderer.bottom() - gap)));
        HyperShotTheme.raisedPanel(graphics, renderer);
        HyperShotTheme.accentBar(graphics, renderer);
        HyperShotTheme.raisedPanel(graphics, system);

        int x = renderer.left() + 12;
        int y = renderer.top() + 10;
        graphics.text(this.font, "RENDERER", x, y, HyperShotTheme.TEXT_DIM, false);
        drawRow(graphics, x, y + 22, "Backend", snapshot.backend());
        drawRow(graphics, x, y + 42, "GPU", snapshot.gpu());
        drawRow(graphics, x, y + 62, "Vendor", snapshot.vendor());
        drawRow(graphics, x, y + 82, "Driver", snapshot.driver());
        drawRow(graphics, x, y + 102, "Max texture", snapshot.maximumTextureSize() + " px");
        drawRow(graphics, x, y + 122, "Output writable", snapshot.outputWritable() ? "Yes" : "No");

        x = system.left() + 12;
        y = system.top() + 10;
        graphics.text(this.font, "SYSTEM", x, y, HyperShotTheme.TEXT_DIM, false);
        drawRow(graphics, x, y + 22, "Heap", HyperShotTheme.humanBytes(snapshot.heapUsed()) + " / " + HyperShotTheme.humanBytes(snapshot.heapMaximum()));
        drawRow(graphics, x, y + 42, "Output space", HyperShotTheme.humanBytes(snapshot.outputUsableBytes()));
        drawRow(graphics, x, y + 62, "Java", snapshot.javaVersion());
        drawRow(graphics, x, y + 82, "Operating system", snapshot.operatingSystem());
        drawRow(graphics, x, y + 102, "HyperShot folder", HyperShotClient.paths().root().getFileName().toString());

        if (!status.isBlank()) {
            graphics.centeredText(this.font, status, layout.content().centerX(), layout.content().bottom() - 16,
                    statusError ? HyperShotTheme.ERROR : HyperShotTheme.SUCCESS);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int x, int y, String label, String value) {
        graphics.text(this.font, label, x, y, HyperShotTheme.TEXT_MUTED, false);
        int valueX = x + 118;
        graphics.text(this.font, value == null || value.isBlank() ? "Unknown" : value, valueX, y, HyperShotTheme.TEXT, false);
    }
}
