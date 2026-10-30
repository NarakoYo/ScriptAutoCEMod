package dev.scriptautoce.client.ui;

import com.mojang.brigadier.ParseResults;
import dev.scriptautoce.client.editor.SyntaxHighlightRegistry;
import dev.scriptautoce.client.editor.SyntaxHighlightRenderer;
import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.policy.CommandPolicy;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.CompiledScript;
import dev.scriptautoce.script.ScriptCompiler;
import dev.scriptautoce.script.ScriptDiagnostic;
import dev.scriptautoce.storage.JsonFiles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class ScriptEditorScreen extends Screen {
    private final Screen parent;
    private final String scriptId;
    private String source;
    private MultiLineEditBox editor;
    private String status = "";
    private ChatFormatting statusColor = ChatFormatting.WHITE;
    private StringWidget statusWidget;
    private List<FoldRegion> foldRegions = List.of();
    private final Set<Integer> collapsedStarts = new HashSet<>();

    public ScriptEditorScreen(Screen parent, String scriptId, String source) {
        super(Component.translatable("screen.scriptautoce.editor"));
        this.parent = parent;
        this.scriptId = scriptId;
        this.source = source;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(new VSCodeButton(4, 28, 48, 18,
                Component.translatable("selectWorld.edit.save"), button -> save()));
        addRenderableWidget(new VSCodeButton(56, 28, 88, 18,
                Component.translatable("screen.scriptautoce.validateCommands"), button -> validate()));
        Button delete = addRenderableWidget(new VSCodeButton(148, 28, 48, 18,
                Component.translatable("selectServer.delete"), button -> confirmDelete()));
        delete.active = scriptId != null;
        addRenderableWidget(new VSCodeButton(width - 28, 4, 24, 18,
                Component.literal("×"), button -> onClose()));

        editor = MultiLineEditBox.builder()
                .setX(54)
                .setY(50)
                .setShowBackground(true)
                .setShowDecorations(true)
                .setTextColor(0xFFD4D4D4)
                .setTextShadow(false)
                .build(font, width - 66, height - 72,
                        Component.translatable("screen.scriptautoce.editor.placeholder"));
        editor.setCharacterLimit(ScriptCompiler.MAX_SOURCE_BYTES);
        editor.setLineLimit(2_000);
        editor.setValue(source);
        editor.setValueListener(value -> {
            source = value;
            recomputeFoldRegions();
        });
        addRenderableWidget(editor);
        SyntaxHighlightRegistry.register(editor);
        recomputeFoldRegions();

        statusWidget = addRenderableWidget(new StringWidget(8, height - 17, width - 16, 10,
                statusComponent(), font));
    }

    private void save() {
        try {
            String id = ClientUiState.localRepository().save(scriptId, source);
            ClientUiState.refreshLocal();
            statusColor = ChatFormatting.GREEN;
            status = Component.translatable("screen.scriptautoce.saved").getString() + " " + id;
            statusWidget.setMessage(statusComponent());
            minecraft.setScreenAndShow(new ScriptEditorScreen(parent, id, source));
        } catch (RuntimeException exception) {
            setError(exception.getMessage());
        }
    }

    private void validate() {
        CompilationResult result = ScriptCompiler.compile(scriptId == null ? "new" : scriptId, source);
        if (!result.success()) {
            ScriptDiagnostic diagnostic = result.diagnostics().stream()
                    .filter(value -> value.severity() == ScriptDiagnostic.Severity.ERROR)
                    .findFirst()
                    .orElse(ScriptDiagnostic.error(1, 1, "Validation failed"));
            setError(diagnostic.line() + ":" + diagnostic.column() + " " + diagnostic.message());
            return;
        }

        CompiledScript script = result.script();
        CommandPolicy policy = policy();
        ClientPacketListener connection = minecraft.getConnection();
        int checked = 0;
        int dynamic = 0;
        for (CompiledScript.Instruction instruction : script.instructions()) {
            if (!(instruction instanceof CompiledScript.RunCommand run)) {
                continue;
            }
            String command = run.command().staticText();
            if (command == null) {
                dynamic++;
                continue;
            }
            command = command.strip();
            while (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isBlank()) {
                setError(run.line() + ":1 run command cannot be empty");
                return;
            }
            CommandPolicy.PolicyDecision decision = policy.evaluate(command);
            if (!decision.allowed()) {
                setError(run.line() + ":1 " + decision.reason());
                return;
            }
            if (connection != null && connection.getCommands() != null) {
                ParseResults<ClientSuggestionProvider> parsed = connection.getCommands()
                        .parse(command, connection.getSuggestionsProvider());
                if (!parsed.getExceptions().isEmpty()
                        || parsed.getContext().getCommand() == null
                        || parsed.getReader().canRead()) {
                    setError(run.line() + ":1 "
                            + Component.translatable("screen.scriptautoce.invalidCommand",
                            command).getString());
                    return;
                }
            }
            checked++;
        }

        boolean commandTreeAvailable = connection != null && connection.getCommands() != null;
        statusColor = !commandTreeAvailable || dynamic > 0
                ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        status = commandTreeAvailable
                ? Component.translatable("screen.scriptautoce.commandsValid",
                        checked, dynamic, script.instructions().size()).getString()
                : Component.translatable("screen.scriptautoce.commandsValidOffline",
                        checked, dynamic).getString();
        statusWidget.setMessage(statusComponent());
    }

    private CommandPolicy policy() {
        try {
            ServerSettings settings = JsonFiles.GSON.fromJson(
                    ClientUiState.serverSettingsJson(), ServerSettings.class);
            return settings == null ? CommandPolicy.defaults() : settings.policy();
        } catch (RuntimeException exception) {
            return CommandPolicy.defaults();
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

    private void setError(String message) {
        statusColor = ChatFormatting.RED;
        status = "error: " + message;
        statusWidget.setMessage(statusComponent());
    }

    private Component statusComponent() {
        String message = status.isBlank() ? scriptId == null ? "untitled.sace" : fileName() : status;
        return Component.literal(message).withStyle(statusColor);
    }

    private String fileName() {
        if (scriptId == null) {
            return "untitled.sace";
        }
        int slash = scriptId.lastIndexOf('/');
        return (slash < 0 ? scriptId : scriptId.substring(slash + 1)) + ".sace";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && editor != null
                && event.x() >= 4 && event.x() < editor.getX()
                && event.y() >= editor.getY() && event.y() < editor.getY() + editor.getHeight()) {
            int line = SyntaxHighlightRenderer.lineAt(editor, event.y());
            FoldRegion region = foldAt(line);
            if (region != null) {
                toggleFold(region.startLine());
                return true;
            }
        }
        if (event.button() == 0 && editor != null && editor.isMouseOver(event.x(), event.y())) {
            int line = SyntaxHighlightRenderer.lineAt(editor, event.y());
            FoldRegion region = collapsedRegionContaining(line);
            if (region != null) {
                collapsedStarts.remove(region.startLine());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!collapsedStarts.isEmpty()) {
            collapsedStarts.clear();
            recomputeFoldRegions();
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!collapsedStarts.isEmpty()) {
            collapsedStarts.clear();
            recomputeFoldRegions();
        }
        return super.charTyped(event);
    }

    private void toggleFold(int startLine) {
        if (!collapsedStarts.remove(startLine)) {
            collapsedStarts.add(startLine);
        }
    }

    private FoldRegion foldAt(int line) {
        return foldRegions.stream().filter(region -> region.startLine() == line)
                .findFirst().orElse(null);
    }

    private FoldRegion collapsedRegionContaining(int line) {
        return foldRegions.stream()
                .filter(region -> collapsedStarts.contains(region.startLine()))
                .filter(region -> line > region.startLine() && line <= region.endLine())
                .findFirst().orElse(null);
    }

    private void recomputeFoldRegions() {
        List<Integer> stack = new ArrayList<>();
        List<FoldRegion> regions = new ArrayList<>();
        int line = 0;
        boolean quoted = false;
        boolean escaped = false;
        boolean comment = false;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '\n') {
                line++;
                comment = false;
                continue;
            }
            if (comment) {
                continue;
            }
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
                continue;
            }
            if (value == '#') {
                comment = true;
            } else if (value == '"') {
                quoted = true;
            } else if (value == '{') {
                stack.add(line);
            } else if (value == '}' && !stack.isEmpty()) {
                int start = stack.removeLast();
                if (line > start) {
                    regions.add(new FoldRegion(start, line));
                }
            }
        }
        foldRegions = List.copyOf(regions);
        collapsedStarts.removeIf(start -> regions.stream()
                .noneMatch(region -> region.startLine() == start));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(0, 0, width, 24, 0xFF181818);
        graphics.fill(0, 24, width, 48, 0xFF252526);
        int tabWidth = Math.min(width - 40, Math.max(140, font.width(fileName()) + 28));
        graphics.fill(8, 4, 8 + tabWidth, 24, 0xFF2D2D30);
        graphics.fill(8, 4, 8 + tabWidth, 6, 0xFF3B82F6);
        graphics.fill(12, 50, editor == null ? 52 : editor.getX() - 2, height - 22, 0xFF1F1F1F);
        graphics.fill(editor == null ? 50 : editor.getX() - 2, 50,
                editor == null ? 51 : editor.getX() - 1, height - 22, 0xFF2B2B2B);
        graphics.fill(0, height - 20, width, height - 19, 0xFF3B82F6);
        graphics.fill(0, height - 19, width, height, 0xFF1F2937);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int tabWidth = Math.min(width - 40, Math.max(140, font.width(fileName()) + 28));
        graphics.centeredText(font, font.plainSubstrByWidth(fileName(), tabWidth - 16),
                8 + tabWidth / 2, 11, 0xFFE7E7E7);
        if (editor == null) {
            return;
        }

        graphics.nextStratum();
        int first = SyntaxHighlightRenderer.firstVisibleLine(editor);
        int visibleLines = Math.max(1, editor.getInnerHeight() / SyntaxHighlightRenderer.LINE_HEIGHT);
        for (int index = 0; index < foldRegions.size(); index++) {
            FoldRegion region = foldRegions.get(index);
            if (!collapsedStarts.contains(region.startLine())) {
                continue;
            }
            int y = SyntaxHighlightRenderer.lineY(editor, region.startLine() + 1);
            int endY = SyntaxHighlightRenderer.lineY(editor, region.endLine())
                    + SyntaxHighlightRenderer.LINE_HEIGHT;
            int top = Math.max(editor.getY(), y);
            int bottom = Math.min(editor.getY() + editor.getInnerHeight() - 2, endY);
            if (bottom > top) {
                graphics.fill(editor.getX() + 1, top, editor.getX() + editor.getWidth() - 10,
                        bottom, 0xFF202020);
                graphics.text(font, "... " + (region.endLine() - region.startLine()) + " lines",
                        editor.getX() + 8, top + 1, 0xFF569CD6, false);
            }
        }

        for (int index = first; index < first + visibleLines; index++) {
            int lineCount = source.split("\\R", -1).length;
            if (index >= lineCount) {
                break;
            }
            FoldRegion containing = collapsedRegionContaining(index);
            if (containing != null) {
                continue;
            }
            int y = SyntaxHighlightRenderer.lineY(editor, index);
            if (y < editor.getY() || y + 8 > editor.getY() + editor.getInnerHeight()) {
                continue;
            }
            String number = Integer.toString(index + 1);
            graphics.text(font, number, 18, y, 0xFF858585, false);
            FoldRegion fold = foldAt(index);
            if (fold != null) {
                String marker = collapsedStarts.contains(index) ? ">" : "v";
                graphics.text(font, marker, 8, y, 0xFF9CDCFE, false);
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

    private record FoldRegion(int startLine, int endLine) {
    }
}
