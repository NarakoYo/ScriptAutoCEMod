package dev.scriptautoce.client.ui;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class ScriptNameScreen extends Screen {
    private final Screen parent;
    private final boolean folder;
    private final Consumer<String> onAccept;
    private EditBox nameBox;
    private String error = "";

    ScriptNameScreen(Screen parent, boolean folder, Consumer<String> onAccept) {
        super(Component.translatable(folder
                ? "screen.scriptautoce.newFolder.title"
                : "screen.scriptautoce.newFile.title"));
        this.parent = parent;
        this.folder = folder;
        this.onAccept = onAccept;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, 24,
                font.width(title) + 4, 10, title, font));
        nameBox = new EditBox(font, width / 2 - 110, 50, 220, 20,
                Component.translatable("screen.scriptautoce.name"));
        nameBox.setHint(Component.translatable(folder
                ? "screen.scriptautoce.newFolder.hint"
                : "screen.scriptautoce.newFile.hint"));
        nameBox.setMaxLength(128);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.create"), button -> accept())
                .bounds(width / 2 - 90, 80, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 6, 80, 84, 20).build());
        if (!error.isBlank()) {
            addRenderableWidget(new StringWidget(width / 2 - 140, 106, 280, 10,
                    Component.literal(error).withStyle(ChatFormatting.RED), font));
        }
    }

    private void accept() {
        String value = nameBox.getValue().strip();
        if (value.isBlank()) {
            error = Component.translatable("screen.scriptautoce.name.empty").getString();
            rebuildWidgets();
            return;
        }
        onAccept.accept(value);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            accept();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(width / 2 - 130, 14, width / 2 + 130, 118, 0xFF252526);
        graphics.fill(width / 2 - 130, 14, width / 2 + 130, 16, 0xFF007ACC);
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
