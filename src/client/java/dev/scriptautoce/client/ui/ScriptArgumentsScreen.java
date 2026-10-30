package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.script.CompiledScript;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScriptArgumentsScreen extends Screen {
    private final Screen parent;
    private final String source;
    private final List<CompiledScript.ScriptArgument> arguments;
    private final Map<String, EditBox> fields = new LinkedHashMap<>();

    public ScriptArgumentsScreen(Screen parent, String scriptId, String source,
                                 List<CompiledScript.ScriptArgument> arguments) {
        super(Component.translatable("screen.scriptautoce.arguments"));
        this.parent = parent;
        this.source = source;
        this.arguments = List.copyOf(arguments);
    }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, 14,
                font.width(title) + 4, 10, title, font));
        int visible = Math.min(arguments.size(), 10);
        int startY = 38;
        for (int index = 0; index < visible; index++) {
            CompiledScript.ScriptArgument argument = arguments.get(index);
            EditBox field = new EditBox(font, width / 2 - 60, startY + index * 27, 180, 20,
                    Component.literal(argument.name()));
            field.setHint(Component.literal(argument.type()));
            field.setMaxLength(256);
            fields.put(argument.name(), field);
            addRenderableWidget(field);
            addRenderableWidget(new StringWidget(4, startY + index * 27 + 6, width / 2 - 72, 10,
                    Component.literal(argument.name() + " (" + argument.type() + ")"), font));
        }
        int footerY = Math.max(startY + visible * 27 + 8, height - 28);
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.run"), button -> run())
                .bounds(width / 2 - 76, footerY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 6, footerY, 70, 20).build());
    }

    private void run() {
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach((name, field) -> {
            if (!field.getValue().isBlank()) {
                values.put(name, field.getValue());
            }
        });
        Runnable action = () -> {
            ClientNetworkController.runSource(source, values);
            onClose();
        };
        if (ClientUiState.settings().confirmBeforeRun()) {
            minecraft.setScreenAndShow(new ScriptConfirmScreen(this, action));
        } else {
            action.run();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFF);
        int visible = Math.min(arguments.size(), 10);
        for (int index = 0; index < visible; index++) {
            CompiledScript.ScriptArgument argument = arguments.get(index);
            int y = 38 + index * 27 + 6;
            graphics.text(font, argument.name() + " (" + argument.type() + ")",
                    4, y, 0xD0D0D0);
        }
        if (arguments.size() > 10) {
            // A fixed 10-field limit keeps the dialog compact and avoids overlapping controls.
        }
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
