package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScriptLibraryScreen extends Screen {
    private enum Filter {
        LOCAL,
        SHARED,
        RUNNING
    }

    private final Screen parent;
    private Filter filter = Filter.LOCAL;
    private String search = "";
    private int page;
    private EditBox searchBox;
    private StringWidget searchHint;

    public ScriptLibraryScreen(Screen parent) {
        super(Component.translatable("screen.scriptautoce.library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        searchBox = new EditBox(font, width / 2 - 210, 38, 190, 20,
                Component.translatable("screen.scriptautoce.search"));
        searchBox.setHint(Component.translatable("screen.scriptautoce.search"));
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            if (searchHint != null) {
                searchHint.visible = value.isBlank();
            }
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        searchHint = addRenderableWidget(new StringWidget(searchBox.getX() + 4, searchBox.getY() + 6,
                Math.min(180, font.width(Component.translatable("screen.scriptautoce.search")) + 4), 10,
                Component.translatable("screen.scriptautoce.search"), font));
        searchHint.visible = search.isBlank();

        addRenderableWidget(Button.builder(filterLabel(), button -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            page = 0;
            if (filter == Filter.SHARED) {
                ClientNetworkController.requestShared(search);
            }
            button.setMessage(filterLabel());
            rebuildWidgets();
        }).bounds(width / 2 + 40, 38, 90, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.settings"),
                button -> minecraft.setScreenAndShow(new ScriptSettingsScreen(this)))
                .bounds(width / 2 + 136, 38, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.refresh"), button -> {
            ClientUiState.refreshLocal();
            ClientNetworkController.requestShared("");
            rebuildWidgets();
        }).bounds(width - 78, 10, 68, 20).build());
        addLabel((width - font.width(title)) / 2, 11, title);
        addLabel(8, 11, Component.translatable(ClientUiState.serverAvailable()
                ? "screen.scriptautoce.mode.server" : "screen.scriptautoce.mode.direct"));

        addPageEntries();
        addFooter();
    }

    private void addPageEntries() {
        List<?> entries = currentEntries();
        int pageSize = effectivePageSize();
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        page = Math.min(page, maxPage);
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = 72;
        for (int index = start; index < end; index++) {
            Object entry = entries.get(index);
            if (entry instanceof LocalScriptRepository.Summary local) {
                int runWidth = 44;
                int shareWidth = 44;
                int mainWidth = width - 11 - runWidth - shareWidth - 4;
                addRenderableWidget(Button.builder(Component.literal(clip(local.title(), 45)),
                        button -> minecraft.setScreenAndShow(
                                new ScriptEditorScreen(this, local.id(), ClientUiState.localRepository().load(local.id()))))
                        .bounds(4, y, mainWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.run"),
                        button -> openArguments(local.id(), ClientUiState.localRepository().load(local.id())))
                        .bounds(4 + mainWidth + 3, y, runWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.share"),
                        button -> ClientNetworkController.share(local.id()))
                        .bounds(4 + mainWidth + runWidth + 6, y, shareWidth, 20).build());
            } else if (entry instanceof SharedScriptStore.Info shared) {
                int downloadWidth = 68;
                int deleteWidth = 48;
                int mainWidth = width - 16 - downloadWidth - deleteWidth;
                addRenderableWidget(Button.builder(Component.literal(clip(shared.title() + "  -  "
                                + shared.ownerName() + "  r" + shared.revision(), 50)),
                        button -> ClientNetworkController.downloadShared(shared.id()))
                        .bounds(4, y, mainWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.download"),
                        button -> ClientNetworkController.downloadShared(shared.id()))
                        .bounds(4 + mainWidth + 3, y, downloadWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("selectServer.delete"),
                        button -> minecraft.setScreenAndShow(new ScriptConfirmScreen(this,
                                Component.translatable("screen.scriptautoce.delete.title"),
                                Component.translatable("screen.scriptautoce.delete.message"),
                                () -> ClientNetworkController.deleteShared(shared.id()))))
                        .bounds(4 + mainWidth + downloadWidth + 7, y, deleteWidth, 20).build());
            } else if (entry instanceof JobEventPayload job) {
                int stopWidth = 50;
                int mainWidth = width - 11 - stopWidth;
                Button status = addRenderableWidget(Button.builder(Component.literal(clip(
                                job.eventType() + "  " + job.progress() + "%  " + job.message(), 50)),
                        button -> {
                        }).bounds(4, y, mainWidth, 20).build());
                status.active = false;
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.stop"),
                        button -> ClientNetworkController.stopJob(job.jobId()))
                        .bounds(4 + mainWidth + 3, y, stopWidth, 20).build());
            }
            y += 24;
        }
        if (entries.isEmpty()) {
            Button empty = addRenderableWidget(Button.builder(
                    Component.translatable("screen.scriptautoce.empty"), button -> {
                    }).bounds(4, 72, width - 8, 20).build());
            empty.active = false;
        }
    }

    private void addFooter() {
        int maxPage = Math.max(0, (currentEntries().size() - 1) / effectivePageSize());
        int footerY = Math.max(80, height - 28);
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(4, footerY, 28, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }).bounds(width - 32, footerY, 28, 20).build());
        next.active = page < maxPage;
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.new"), button ->
                        minecraft.setScreenAndShow(new ScriptEditorScreen(this, null,
                                "meta name \"new_script\"\n\nlog \"Hello from Script Auto CE\"\n")))
                .bounds(width / 2 - 140, footerY, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 76, footerY, 72, 20).build());
        String pageText = (page + 1) + " / " + (maxPage + 1);
        addLabel(width - 35 - font.width(pageText), footerY + 6, Component.literal(pageText));
    }

    private void openArguments(String id, String source) {
        var compilation = dev.scriptautoce.script.ScriptCompiler.compile(id, source);
        if (!compilation.success()) {
            ClientUiState.message(compilation.diagnostics().getFirst().toString());
            return;
        }
        if (compilation.script().arguments().isEmpty()) {
            Runnable action = () -> ClientNetworkController.runSource(source, java.util.Map.of());
            if (ClientUiState.settings().confirmBeforeRun()) {
                minecraft.setScreenAndShow(new ScriptConfirmScreen(this, action));
            } else {
                action.run();
            }
        } else {
            minecraft.setScreenAndShow(new ScriptArgumentsScreen(this, id, source,
                    compilation.script().arguments()));
        }
    }

    private List<?> currentEntries() {
        String query = search.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (filter) {
            case LOCAL -> ClientUiState.localScripts().stream()
                    .filter(script -> query.isBlank()
                            || script.title().toLowerCase(java.util.Locale.ROOT).contains(query)
                            || script.id().contains(query))
                    .toList();
            case SHARED -> ClientUiState.sharedScripts().stream()
                    .filter(script -> query.isBlank()
                            || script.title().toLowerCase(java.util.Locale.ROOT).contains(query)
                            || script.ownerName().toLowerCase(java.util.Locale.ROOT).contains(query))
                    .toList();
            case RUNNING -> ClientUiState.jobs().values().stream().toList();
        };
    }

    private Component filterLabel() {
        return Component.translatable("screen.scriptautoce.filter." + filter.name().toLowerCase());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 15, 0xFFFFFF);
        String mode = ClientUiState.serverAvailable()
                ? Component.translatable("screen.scriptautoce.mode.server").getString()
                : Component.translatable("screen.scriptautoce.mode.direct").getString();
        graphics.text(font, Component.literal(mode), 12, 15, 0xA0A0A0);
        String pageText = (page + 1) + " / " + (Math.max(0,
                (currentEntries().size() - 1) / effectivePageSize()) + 1);
        graphics.text(font, pageText, width / 2 + 130, height - 23, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private String clip(String value, int maxChars) {
        String clipped = value.length() <= maxChars ? value : value.substring(0, maxChars - 3) + "...";
        return font.plainSubstrByWidth(clipped, Math.max(40, width - 140));
    }

    private int effectivePageSize() {
        int availableRows = Math.max(1, (height - 108) / 24);
        return Math.min(ClientUiState.settings().pageSize(), availableRows);
    }

    private StringWidget addLabel(int x, int y, Component text) {
        return addRenderableWidget(new StringWidget(x, y, Math.max(20, width - x - 4), 10, text, font));
    }
}
