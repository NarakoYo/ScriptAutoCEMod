package dev.scriptautoce.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ExplorerRowButton extends Button {
    public enum IconKind {
        FOLDER,
        FILE,
        SHARED,
        JOB
    }

    private final IconKind iconKind;
    private final String subtitle;
    private final int accent;

    public ExplorerRowButton(int x, int y, int width, int height, Component message,
                             String subtitle, IconKind iconKind, int accent, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.iconKind = iconKind;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accent = accent;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int background = !active ? 0xFF252526 : isHoveredOrFocused() ? 0xFF37373D : 0xFF2A2D2E;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(),
                isHoveredOrFocused() && active ? 0xFF3B82F6 : 0xFF202020);
        drawIcon(graphics);

        var font = Minecraft.getInstance().font;
        String suffix = subtitle.isBlank() ? "" : subtitle;
        int suffixWidth = suffix.isBlank() ? 0 : font.width(suffix);
        int nameX = getX() + 25;
        int maxNameWidth = Math.max(10, getWidth() - 31 - suffixWidth);
        String name = font.plainSubstrByWidth(getMessage().getString(), maxNameWidth);
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.text(font, name, nameX, textY, active ? 0xFFE7E7E7 : 0xFF737373, false);
        if (!suffix.isBlank()) {
            graphics.text(font, suffix, getX() + getWidth() - suffixWidth - 6, textY,
                    active ? 0xFF9A9A9A : 0xFF666666, false);
        }
    }

    private void drawIcon(GuiGraphicsExtractor graphics) {
        int x = getX() + 7;
        int y = getY() + 4;
        if (iconKind == IconKind.FOLDER) {
            graphics.fill(x + 1, y + 2, x + 6, y + 5, 0xFF9B7B36);
            graphics.fill(x, y + 4, x + 11, y + 11, 0xFFD7BA7D);
            graphics.fill(x + 1, y + 5, x + 10, y + 10, 0xFFE8CE91);
            return;
        }

        int paper = iconKind == IconKind.SHARED ? 0xFFBDEBE4 : 0xFFD7D7D7;
        graphics.fill(x + 2, y, x + 10, y + 11, paper);
        graphics.fill(x + 7, y, x + 10, y + 3, 0xFF7C7C7C);
        graphics.fill(x + 4, y + 4, x + 8, y + 5, accent);
        graphics.fill(x + 4, y + 7, x + 8, y + 8, accent);
        if (iconKind == IconKind.SHARED) {
            graphics.fill(x + 8, y + 8, x + 11, y + 11, 0xFF4EC9B0);
        } else if (iconKind == IconKind.JOB) {
            graphics.fill(x + 7, y + 7, x + 10, y + 10, accent);
        }
    }
}
