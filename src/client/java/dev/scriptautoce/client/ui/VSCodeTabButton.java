package dev.scriptautoce.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class VSCodeTabButton extends Button {
    private boolean selected;

    public VSCodeTabButton(int x, int y, int width, int height, Component message,
                           boolean selected, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int background = selected
                ? 0xFF1E1E1E
                : isHoveredOrFocused() ? 0xFF3A3D41 : 0xFF2D2D30;
        int text = selected ? 0xFFFFFFFF : 0xFFCCCCCC;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        if (selected) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 2, 0xFF3B82F6);
            graphics.fill(getX(), getY() + getHeight() - 1,
                    getX() + getWidth(), getY() + getHeight(), 0xFF3B82F6);
        }
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(
                getMessage().getString(), Math.max(1, getWidth() - 8));
        graphics.centeredText(Minecraft.getInstance().font, clipped,
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, text);
    }
}
