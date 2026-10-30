package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.editor.SyntaxHighlightRegistry;
import dev.scriptautoce.client.editor.SyntaxHighlightRenderer;
import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.ScriptCompiler;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
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
        addRenderableWidget(new StringWidget(10, 7, width - 20, 10,
                Component.literal(fileName()).withStyle(ChatFormatting.WHITE), font));
        addRenderableWidget(Button.builder(Component.translatable("selectWorld.edit.save"), button -> save())
                .bounds(4, 28, 45, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.validate"),
                button -> validate()).bounds(53, 28, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.run"),
                button -> run()).bounds(115, 28, 44, 18).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("selectServer.delete"),
                button -> confirmDelete()).bounds(163, 28, 46, 18).build());
        delete.active = scriptId != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width - 48, 28, 44, 18).build());

        editor = MultiLineEditBox.builder()
                .setX(40)
                .setY(50)
                .setShowBackground(true)
                .setShowDecorations(true)
                .setTextColor(0x00FFFFFF)
                .setTextShadow(false)
                .build(font, width - 52, height - 72,
                        Component.translatable("screen.scriptautoce.editor.placeholder"));
        editor.setCharacterLimit(ScriptCompiler.MAX_SOURCE_BYTES);
        editor.setLineLimit(2_000);
        editor.setValue(source);
        editor.setValueListener(value -> source = value);
        addRenderableWidget(editor);
        SyntaxHighlightRegistry.register(editor);

        statusWidget = addRenderableWidget(new StringWidget(8, height - 18, width - 16, 10,
                statusComponent(), font));
    }

    private void save() {
        try {
            String id = ClientUiState.localRepository().save(scriptId, source);
            ClientUiState.refreshLocal();
            status = Component.translatable("screen.scriptautoce.saved").getString() + " " + id;
            statusWidget.setMessage(statusComponent());
            minecraft.setScreenAndShow(new ScriptEditorScreen(parent, id, source));
        } catch (RuntimeException exception) {
            status = exception.getMessage();
            statusWidget.setMessage(statusComponent());
        }
    }

    private void validate() {
        CompilationResult result = ScriptCompiler.compile(scriptId == null ? "new" : scriptId, source);
        status = result.success()
                ? Component.translatable("screen.scriptautoce.valid",
                result.script().instructions().size()).getString()
                : result.diagnostics().getFirst().toString();
        statusWidget.setMessage(statusComponent());
    }

    private void run() {
        CompilationResult result = ScriptCompiler.compile(scriptId == null ? "new" : scriptId, source);
        if (!result.success()) {
            status = result.diagnostics().getFirst().toString();
            statusWidget.setMessage(statusComponent());
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

    private Component statusComponent() {
        ChatFormatting color = status.startsWith("error:") ? ChatFormatting.RED : ChatFormatting.WHITE;
        String message = status.isBlank() ? scriptId == null ? "untitled.sace" : fileName() : status;
        return Component.literal(message).withStyle(color);
    }

    private String fileName() {
        if (scriptId == null) {
            return "untitled.sace";
        }
        int slash = scriptId.lastIndexOf('/');
        return (slash < 0 ? scriptId : scriptId.substring(slash + 1)) + ".sace";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(0, 0, width, 24, 0xFF252526);
        graphics.fill(0, 24, width, 48, 0xFF2D2D30);
        graphics.fill(8, 4, Math.min(width - 8, 8 + font.width(fileName()) + 20), 23, 0xFF1E1E1E);
        graphics.fill(8, 4, Math.min(width - 8, 8 + font.width(fileName()) + 20), 6, 0xFF007ACC);
        graphics.fill(12, 50, 38, height - 22, 0xFF252526);
        graphics.fill(0, height - 20, width, height, 0xFF007ACC);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (editor != null) {
            graphics.nextStratum();
            List<String> numbers = SyntaxHighlightRenderer.lineNumbers(editor,
                    Math.max(1, editor.getHeight() / 9), editor.scrollAmount());
            int y = editor.getY() + 4;
            for (String number : numbers) {
                graphics.text(font, number, 34 - font.width(number), y, 0xFF858585, false);
                y += 9;
            }
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
