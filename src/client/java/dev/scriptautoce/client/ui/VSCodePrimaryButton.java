package dev.scriptautoce.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class VSCodePrimaryButton extends Button {
    public VSCodePrimaryButton(int x, int y, int width, int height,
                               Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int background = !active ? 0xFF2E3A46 : isHoveredOrFocused() ? 0xFF2B74B8 : 0xFF0E639C;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.fill(getX(), getY() + getHeight() - 1,
                getX() + getWidth(), getY() + getHeight(), 0xFF1B4F72);
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(
                getMessage().getString(), Math.max(1, getWidth() - 8));
        graphics.centeredText(Minecraft.getInstance().font, clipped,
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
    }
}
