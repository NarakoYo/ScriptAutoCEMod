package dev.scriptautoce.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class VSCodeButton extends Button {
    public VSCodeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int background = !active ? 0xFF252526 : isHoveredOrFocused() ? 0xFF3A3D41 : 0xFF333333;
        int textColor = active ? 0xFFD4D4D4 : 0xFF737373;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(),
                isHoveredOrFocused() && active ? 0xFF3B82F6 : 0xFF252526);
        String text = getMessage().getString();
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(text, Math.max(1, getWidth() - 6));
        graphics.centeredText(Minecraft.getInstance().font, clipped,
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
    }
}
