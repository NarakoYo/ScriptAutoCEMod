package dev.scriptautoce.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ScriptConfirmScreen extends Screen {
    private final Screen parent;
    private final Runnable action;
    private final Component message;
    private final Component confirmText;

    ScriptConfirmScreen(Screen parent, Runnable action) {
        this(parent, Component.translatable("screen.scriptautoce.confirm.title"),
                Component.translatable("screen.scriptautoce.confirm.message"),
                Component.translatable("screen.scriptautoce.run"), action);
    }

    ScriptConfirmScreen(Screen parent, Component title, Component message, Runnable action) {
        this(parent, title, message, Component.translatable("gui.ok"), action);
    }

    ScriptConfirmScreen(Screen parent, Component title, Component message,
                        Component confirmText, Runnable action) {
        super(title);
        this.parent = parent;
        this.action = action;
        this.message = message;
        this.confirmText = confirmText;
    }

    @Override
    protected void init() {
        clearWidgets();
        int y = height / 2 + 12;
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, height / 2 - 28,
                font.width(title) + 4, 10, title, font));
        addRenderableWidget(new StringWidget((width - font.width(message)) / 2, height / 2 - 8,
                font.width(message) + 4, 10, message, font));
        addRenderableWidget(Button.builder(confirmText, button -> {
            action.run();
        }).bounds(width / 2 - 82, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                button -> minecraft.setScreenAndShow(parent))
                .bounds(width / 2 + 6, y, 76, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 28, 0xFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.scriptautoce.confirm.message"),
                width / 2, height / 2 - 8, 0xD0D0D0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
