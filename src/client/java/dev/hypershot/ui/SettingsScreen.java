package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.config.MetadataPrivacy;
import dev.hypershot.core.OutputFormat;
import dev.hypershot.core.UiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SettingsScreen extends HyperShotScreen {
    private Section section = Section.CAPTURE;
    private int optionPage;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.hypershot.settings.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        UiLayout layout = layout(true);
        int titleTop = contentTop(layout);
        if (layout.sidebar().width() > 0) {
            int y = layout.sidebar().top() + 10;
            for (Section candidate : Section.values()) {
                Button button = this.addRenderableWidget(Button.builder(candidate.title, b -> selectSection(candidate))
                        .bounds(layout.sidebar().left() + 8, y, layout.sidebar().width() - 16, 22).build());
                button.active = candidate != section;
                y += 26;
            }
        } else {
            int x = layout.content().left() + 10;
            int width = layout.content().width() - 20;
            this.addRenderableWidget(Button.builder(Component.literal("‹"), b -> changeSection(-1))
                    .bounds(x, layout.content().top() + 8, 28, 20).build());
            Button current = this.addRenderableWidget(Button.builder(section.title, b -> {})
                    .bounds(x + 34, layout.content().top() + 8, Math.max(1, width - 68), 20).build());
            current.active = false;
            this.addRenderableWidget(Button.builder(Component.literal("›"), b -> changeSection(1))
                    .bounds(x + width - 28, layout.content().top() + 8, 28, 20).build());
            titleTop += 30;
        }

        List<SettingControl> controls = controlsFor(section);
        int controlsTop = titleTop + 40;
        int availableHeight = Math.max(20, layout.content().bottom() - controlsTop - 8);
        int visibleRows = Math.max(1, availableHeight / 26);
        int pages = Math.max(1, (controls.size() + visibleRows - 1) / visibleRows);
        optionPage = Math.max(0, Math.min(optionPage, pages - 1));
        int start = optionPage * visibleRows;
        int end = Math.min(controls.size(), start + visibleRows);
        int x = contentLeft(layout);
        int width = Math.min(440, Math.max(1, layout.content().width() - 28));
        int y = controlsTop;
        for (int i = start; i < end; i++) {
            SettingControl control = controls.get(i);
            this.addRenderableWidget(Button.builder(control.label, b -> control.action.run())
                    .bounds(x, y, width, 20).build());
            y += 26;
        }

        if (pages > 1) {
            List<UiLayout.Rect> footer = footerButtons(layout, 4);
            Button previous = addButton(footer.get(0), Component.literal("Previous options"), () -> changeOptionPage(-1));
            previous.active = optionPage > 0;
            Button next = addButton(footer.get(1), Component.literal("Next options"), () -> changeOptionPage(1));
            next.active = optionPage + 1 < pages;
            addButton(footer.get(2), Component.translatable("screen.hypershot.settings.gallery"), () -> HyperShotClient.openGallery(this));
            addButton(footer.get(3), Component.translatable("gui.done"), this::onClose);
        } else {
            List<UiLayout.Rect> footer = footerButtons(layout, 2);
            addButton(footer.get(0), Component.translatable("screen.hypershot.settings.gallery"), () -> HyperShotClient.openGallery(this));
            addButton(footer.get(1), Component.translatable("gui.done"), this::onClose);
        }
    }

    private Button addButton(UiLayout.Rect rect, Component label, Runnable action) {
        return this.addRenderableWidget(Button.builder(label, b -> action.run())
                .bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
    }

    private List<SettingControl> controlsFor(Section current) {
        CapturePreset preset = HyperShotClient.config().activePreset();
        List<SettingControl> controls = new ArrayList<>();
        switch (current) {
            case CAPTURE -> {
                controls.add(toggle("Replace vanilla F2", HyperShotClient.config().replaceVanillaF2,
                        () -> HyperShotClient.config().replaceVanillaF2 = !HyperShotClient.config().replaceVanillaF2));
                controls.add(toggle("Show HyperShot menu button", HyperShotClient.config().showMenuButton,
                        () -> HyperShotClient.config().showMenuButton = !HyperShotClient.config().showMenuButton));
                controls.add(toggle("Hide HUD in active preset", preset.hideHud, () -> preset.hideHud = !preset.hideHud));
                controls.add(toggle("Hide hand in active preset", preset.hideHand, () -> preset.hideHand = !preset.hideHand));
                controls.add(toggle("Hide selection outline", preset.hideBlockOutline, () -> preset.hideBlockOutline = !preset.hideBlockOutline));
            }
            case OUTPUT -> {
                controls.add(action("Format:  " + preset.outputFormat.name(), () ->
                        preset.outputFormat = preset.outputFormat == OutputFormat.PNG ? OutputFormat.JPEG : OutputFormat.PNG));
                if (preset.outputFormat == OutputFormat.PNG) {
                    controls.add(action("PNG compression:  " + preset.pngCompression, () ->
                            preset.pngCompression = preset.pngCompression >= 9 ? 0 : preset.pngCompression + 1));
                } else {
                    controls.add(action("JPEG quality:  " + Math.round(preset.jpegQuality * 100) + "%", () -> {
                        int quality = Math.round(preset.jpegQuality * 100);
                        preset.jpegQuality = (quality >= 100 ? 70 : quality + 5) / 100.0f;
                    }));
                }
                controls.add(toggle("Write JSON metadata", preset.includeMetadata, () -> preset.includeMetadata = !preset.includeMetadata));
                controls.add(action("Metadata privacy:  " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().metadataPrivacy), () -> {
                    MetadataPrivacy[] values = MetadataPrivacy.values();
                    HyperShotClient.config().metadataPrivacy = values[(HyperShotClient.config().metadataPrivacy.ordinal() + 1) % values.length];
                }));
            }
            case NOTIFICATIONS -> {
                controls.add(toggle("Screenshot preview cards", HyperShotClient.config().notificationsEnabled,
                        () -> HyperShotClient.config().notificationsEnabled = !HyperShotClient.config().notificationsEnabled));
                controls.add(action("Dismiss after:  " + HyperShotClient.config().notificationSeconds + " seconds", () -> {
                    int value = HyperShotClient.config().notificationSeconds;
                    HyperShotClient.config().notificationSeconds = value >= 20 ? 4 : value + 4;
                }));
            }
            case PERFORMANCE -> {
                controls.add(action("Tile size:  " + preset.tileSize + " px", () -> {
                    preset.tileSize = switch (preset.tileSize) {
                        case 1024 -> 2048;
                        case 2048 -> 4096;
                        default -> 1024;
                    };
                    if (preset.overlap >= preset.tileSize / 2) preset.overlap = 0;
                }));
                controls.add(action("Tile overlap:  " + preset.overlap + " px", () -> preset.overlap = switch (preset.overlap) {
                    case 0 -> 16;
                    case 16 -> 32;
                    case 32 -> 64;
                    default -> 0;
                }));
                controls.add(action("Reserved free disk:  " + HyperShotTheme.humanBytes(HyperShotClient.config().freeDiskMarginBytes), () -> {
                    long gib = 1024L * 1024 * 1024;
                    long current = HyperShotClient.config().freeDiskMarginBytes;
                    HyperShotClient.config().freeDiskMarginBytes = current >= 8 * gib ? gib : current * 2;
                }));
            }
            case GALLERY -> {
                controls.add(navigation("Open screenshot gallery", () -> HyperShotClient.openGallery(this)));
                controls.add(navigation("Open HyperShot folder", () -> HyperShotClient.platform().open(HyperShotClient.paths().root())));
            }
            case DIAGNOSTICS -> {
                controls.add(navigation("Open diagnostics", () -> this.minecraft.gui.setScreen(new DiagnosticsScreen(this))));
                controls.add(navigation("Open logs folder", () -> HyperShotClient.platform().open(HyperShotClient.paths().logs())));
            }
        }
        return List.copyOf(controls);
    }

    private SettingControl toggle(String label, boolean value, Runnable mutation) {
        return action((value ? "ON   " : "OFF  ") + label, mutation);
    }

    private SettingControl action(String label, Runnable mutation) {
        return new SettingControl(Component.literal(label), () -> {
            mutation.run();
            persistAndRebuild();
        });
    }

    private SettingControl navigation(String label, Runnable action) {
        return new SettingControl(Component.literal(label), action);
    }

    private void selectSection(Section candidate) {
        section = candidate;
        optionPage = 0;
        rebuildWidgets();
    }

    private void changeSection(int direction) {
        Section[] values = Section.values();
        selectSection(values[Math.floorMod(section.ordinal() + direction, values.length)]);
    }

    private void changeOptionPage(int direction) {
        optionPage = Math.max(0, optionPage + direction);
        rebuildWidgets();
    }

    private void persistAndRebuild() {
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiLayout layout = layout(true);
        drawChrome(graphics, layout, Component.literal("Active preset: " + HyperShotClient.config().activePreset().name));
        drawContentPanels(graphics, layout);
        int titleTop = contentTop(layout) + (layout.sidebar().width() == 0 ? 30 : 0);
        UiLayout.Rect titleArea = new UiLayout.Rect(contentLeft(layout), titleTop, Math.max(1, layout.content().width() - 28), 36);
        HyperShotTheme.sectionTitle(graphics, this.font, section.title, section.description, titleArea);
    }

    private record SettingControl(Component label, Runnable action) {}

    private enum Section {
        CAPTURE(Component.literal("Capture"), Component.literal("Keys and what is hidden during the rendered screenshot.")),
        OUTPUT(Component.literal("Output"), Component.literal("Image format, compression, and metadata privacy.")),
        NOTIFICATIONS(Component.literal("Notifications"), Component.literal("Clickable in-game screenshot preview cards.")),
        PERFORMANCE(Component.literal("Performance"), Component.literal("Advanced tile and disk-safety controls for extreme captures.")),
        GALLERY(Component.literal("Gallery"), Component.literal("Open and manage the indexed screenshot library.")),
        DIAGNOSTICS(Component.literal("Diagnostics"), Component.literal("Renderer, storage, codec, and recent-error information."));

        final Component title;
        final Component description;

        Section(Component title, Component description) {
            this.title = title;
            this.description = description;
        }
    }
}
