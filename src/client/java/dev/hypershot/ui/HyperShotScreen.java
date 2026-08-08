package dev.hypershot.ui;

import dev.hypershot.core.UiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class HyperShotScreen extends Screen {
    protected final Screen parent;

    protected HyperShotScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        HyperShotTheme.background(graphics, this.width, this.height);
    }

    protected UiLayout layout(boolean sidebar) {
        return UiLayout.compute(this.width, this.height, sidebar);
    }

    protected void drawChrome(GuiGraphicsExtractor graphics, UiLayout layout, Component subtitle) {
        HyperShotTheme.raisedPanel(graphics, layout.header());
        HyperShotTheme.accentBar(graphics, layout.header());
        graphics.text(this.font, this.title, layout.header().left() + 12, layout.header().top() + 8, HyperShotTheme.TEXT, true);
        if (subtitle != null && layout.header().width() >= 420) {
            graphics.text(this.font, subtitle, layout.header().right() - 12 - this.font.width(subtitle),
                    layout.header().top() + 8, HyperShotTheme.TEXT_MUTED, false);
        }
        HyperShotTheme.raisedPanel(graphics, layout.footer());
    }

    protected void drawContentPanels(GuiGraphicsExtractor graphics, UiLayout layout) {
        if (layout.sidebar().width() > 0) HyperShotTheme.panel(graphics, layout.sidebar());
        HyperShotTheme.panel(graphics, layout.content());
    }

    protected int contentLeft(UiLayout layout) { return layout.content().left() + 14; }
    protected int contentRight(UiLayout layout) { return layout.content().right() - 14; }
    protected int contentTop(UiLayout layout) { return layout.content().top() + 14; }

    protected java.util.List<UiLayout.Rect> footerButtons(UiLayout layout, int count) {
        UiLayout.Rect row = new UiLayout.Rect(layout.footer().left() + 8, layout.footer().top() + 7,
                Math.max(1, layout.footer().width() - 16), 20);
        return UiLayout.distribute(row, count, layout.compact() ? 4 : 6);
    }
}
