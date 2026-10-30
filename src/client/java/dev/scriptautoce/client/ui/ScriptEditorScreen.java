package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.ScriptCompiler;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScriptEditorScreen extends Screen {
    private final Screen parent;
    private final String scriptId;
    private String source;
    private MultiLineEditBox editor;
    private String status = "";
    private StringWidget statusWidget;

    public ScriptEditorScreen(Screen parent, String scriptId, String source) {
        super(Component.translatable("screen.scriptautoce.editor"));
        this.parent = parent;
        this.scriptId = scriptId;
        this.source = source;
    }

    @Override
    protected void init() {
        clearWidgets();
        editor = MultiLineEditBox.builder()
                .setX(16)
                .setY(32)
                .setShowBackground(true)
                .setShowDecorations(true)
                .build(font, width - 32, height - 72,
                        Component.translatable("screen.scriptautoce.editor.placeholder"));
        editor.setCharacterLimit(ScriptCompiler.MAX_SOURCE_BYTES);
        editor.setLineLimit(2_000);
        editor.setValue(source);
        editor.setValueListener(value -> source = value);
        addRenderableWidget(editor);
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, 10,
                font.width(title) + 4, 10, title, font));
        statusWidget = addRenderableWidget(new StringWidget(16, height - 44, width - 32, 10,
                Component.literal(status), font));

        int y = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("selectWorld.edit.save"), button -> save())
                .bounds(16, y, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.validate"), button -> validate())
                .bounds(72, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.run"), button -> run())
                .bounds(144, y, 46, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("selectServer.delete"),
                button -> confirmDelete()).bounds(196, y, 50, 20).build());
        delete.active = scriptId != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width - 62, y, 46, 20).build());
    }

    private void save() {
        try {
            String id = ClientUiState.localRepository().save(scriptId, source);
            ClientUiState.refreshLocal();
            status = Component.translatable("screen.scriptautoce.saved").getString() + " " + id;
            statusWidget.setMessage(Component.literal(status));
            minecraft.setScreenAndShow(new ScriptEditorScreen(parent, id, source));
        } catch (RuntimeException exception) {
            status = exception.getMessage();
            statusWidget.setMessage(Component.literal(status));
        }
    }

    private void validate() {
        CompilationResult result = ScriptCompiler.compile(scriptId == null ? "new" : scriptId, source);
        if (result.success()) {
            status = Component.translatable("screen.scriptautoce.valid",
                    result.script().instructions().size()).getString();
        } else {
            status = result.diagnostics().getFirst().toString();
        }
        statusWidget.setMessage(Component.literal(status));
    }

    private void run() {
        CompilationResult result = ScriptCompiler.compile(scriptId == null ? "new" : scriptId, source);
        if (!result.success()) {
            status = result.diagnostics().getFirst().toString();
            statusWidget.setMessage(Component.literal(status));
            return;
        }
        if (result.script().arguments().isEmpty()) {
            Runnable action = () -> {
                ClientNetworkController.runSource(source, Map.of());
                onClose();
            };
            if (ClientUiState.settings().confirmBeforeRun()) {
                minecraft.setScreenAndShow(new ScriptConfirmScreen(this, action));
            } else {
                action.run();
            }
        } else {
            minecraft.setScreenAndShow(new ScriptArgumentsScreen(this, scriptId, source,
                    result.script().arguments()));
        }
    }

    private void confirmDelete() {
        if (scriptId == null) {
            return;
        }
        minecraft.setScreenAndShow(new ScriptConfirmScreen(this,
                Component.translatable("screen.scriptautoce.delete.title"),
                Component.translatable("screen.scriptautoce.delete.message"),
                () -> {
                    ClientUiState.localRepository().delete(scriptId);
                    ClientUiState.refreshLocal();
                    minecraft.setScreenAndShow(parent);
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF);
        if (!status.isBlank()) {
            graphics.text(font, status, 16, height - 44, 0xD0D0D0);
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
