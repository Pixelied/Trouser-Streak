package dev.hypershot.ui;

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
    public void onClose() { this.minecraft.gui.setScreen(parent); }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF111419, 0xFF1A1E25);
        graphics.fill(0, 0, this.width, 34, 0xE80D0F13);
        graphics.fill(0, 33, this.width, 34, 0xFF343A45);
    }

    protected void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, this.title, 16, 12, 0xFFFFFFFF, true);
    }
}
