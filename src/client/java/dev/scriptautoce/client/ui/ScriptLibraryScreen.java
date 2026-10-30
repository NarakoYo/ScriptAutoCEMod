package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
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
    private String currentFolder = "";
    private String search = "";
    private int page;
    private EditBox searchBox;

    public ScriptLibraryScreen(Screen parent) {
        super(Component.translatable("screen.scriptautoce.library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(new StringWidget(10, 10, 110, 10,
                Component.literal("SCRIPT AUTO CE").withStyle(ChatFormatting.BOLD), font));

        searchBox = new EditBox(font, 4, 34, 135, 20,
                Component.translatable("screen.scriptautoce.search"));
        searchBox.setHint(Component.translatable("screen.scriptautoce.search"));
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        if (search.isBlank()) {
            addRenderableWidget(new StringWidget(8, 40, 120, 10,
                    Component.translatable("screen.scriptautoce.search").withStyle(ChatFormatting.GRAY), font));
        }

        addRenderableWidget(Button.builder(filterLabel(), button -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            page = 0;
            if (filter == Filter.SHARED) {
                ClientNetworkController.requestShared(search);
            }
            rebuildWidgets();
        }).bounds(143, 34, 58, 20).build());

        Button newFile = addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.newFile"),
                button -> createScript()).bounds(205, 34, 64, 20).build());
        newFile.active = filter == Filter.LOCAL;
        Button newFolder = addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.newFolder"),
                button -> createFolder()).bounds(273, 34, 66, 20).build());
        newFolder.active = filter == Filter.LOCAL;
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.settings"),
                button -> minecraft.setScreenAndShow(new ScriptSettingsScreen(this)))
                .bounds(343, 34, Math.max(76, width - 347), 20).build());
        addRenderableWidget(Button.builder(Component.literal("↻"), button -> {
            ClientUiState.refreshLocal();
            ClientNetworkController.requestShared(search);
            rebuildWidgets();
        }).bounds(width - 74, 8, 66, 18).build());

        if (filter == Filter.LOCAL) {
            buildFolderSidebar();
            buildLocalFileList();
        } else {
            buildNonLocalList();
        }
        buildFooter();
    }

    private void buildFolderSidebar() {
        int sidebarWidth = 104;
        int x = 4;
        int y = 62;
        Button root = addRenderableWidget(Button.builder(Component.literal("ScriptAutoCE"),
                button -> {
                    currentFolder = "";
                    page = 0;
                    rebuildWidgets();
                }).bounds(x, y, sidebarWidth, 18).build());
        root.active = !currentFolder.isBlank();
        y += 20;

        if (!currentFolder.isBlank()) {
            addRenderableWidget(Button.builder(Component.literal(".."), button -> {
                currentFolder = parentOf(currentFolder);
                page = 0;
                rebuildWidgets();
            }).bounds(x, y, sidebarWidth, 18).build());
            y += 20;
        }

        for (String folder : childFolders()) {
            if (y > height - 58) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal("> " + folderName(folder)), button -> {
                currentFolder = folder;
                page = 0;
                rebuildWidgets();
            }).bounds(x, y, sidebarWidth, 18).build());
            y += 20;
        }
    }

    private void buildLocalFileList() {
        int left = 116;
        int rightWidth = width - left - 4;
        addRenderableWidget(new StringWidget(left, 60, rightWidth, 10,
                listHeader().copy().withStyle(ChatFormatting.GRAY), font));
        List<LocalScriptRepository.Summary> entries = visibleLocalScripts();
        int startY = 76;
        int rowHeight = 22;
        int pageSize = effectivePageSize(startY);
        int maxPage = maxPage(entries.size(), pageSize);
        page = Math.min(page, maxPage);
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = startY;
        for (int index = start; index < end; index++) {
            LocalScriptRepository.Summary script = entries.get(index);
            int runWidth = 42;
            int shareWidth = 42;
            int nameWidth = Math.max(80, rightWidth - runWidth - shareWidth - 6);
            String displayName = search.isBlank() ? script.fileName() : script.relativePath();
            addRenderableWidget(Button.builder(Component.literal(clip(displayName, nameWidth - 8)),
                    button -> openEditor(script.id()))
                    .bounds(left, y, nameWidth, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.run"),
                    button -> openArguments(script.id(), ClientUiState.localRepository().load(script.id())))
                    .bounds(left + nameWidth + 3, y, runWidth, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.share"),
                    button -> ClientNetworkController.share(script.id()))
                    .bounds(left + nameWidth + runWidth + 6, y, shareWidth, 18).build());
            y += rowHeight;
        }
        if (entries.isEmpty()) {
            addRenderableWidget(new StringWidget(left, startY + 4, rightWidth, 10,
                    Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY), font));
        }
    }

    private void buildNonLocalList() {
        if (filter == Filter.SHARED) {
            List<SharedScriptStore.Info> entries = filteredShared();
            int y = 62;
            int pageSize = effectivePageSize(y);
            int maxPage = maxPage(entries.size(), pageSize);
            page = Math.min(page, maxPage);
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            for (int index = start; index < end; index++) {
                SharedScriptStore.Info shared = entries.get(index);
                int downloadWidth = 64;
                int deleteWidth = 44;
                int nameWidth = width - 12 - downloadWidth - deleteWidth;
                addRenderableWidget(Button.builder(Component.literal(clip(
                                shared.title() + "  -  " + shared.ownerName() + "  r" + shared.revision(),
                                nameWidth - 8)),
                        button -> ClientNetworkController.downloadShared(shared.id()))
                        .bounds(4, y, nameWidth, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.download"),
                        button -> ClientNetworkController.downloadShared(shared.id()))
                        .bounds(7 + nameWidth, y, downloadWidth, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("selectServer.delete"),
                        button -> minecraft.setScreenAndShow(new ScriptConfirmScreen(this,
                                Component.translatable("screen.scriptautoce.delete.title"),
                                Component.translatable("screen.scriptautoce.delete.message"),
                                () -> ClientNetworkController.deleteShared(shared.id()))))
                        .bounds(7 + nameWidth + downloadWidth, y, deleteWidth, 18).build());
                y += 22;
            }
            if (entries.isEmpty()) {
                addRenderableWidget(new StringWidget(4, 66, width - 8, 10,
                        Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY), font));
            }
        } else {
            List<JobEventPayload> entries = List.copyOf(ClientUiState.jobs().values());
            int y = 62;
            int pageSize = effectivePageSize(y);
            int maxPage = maxPage(entries.size(), pageSize);
            page = Math.min(page, maxPage);
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            for (int index = start; index < end; index++) {
                JobEventPayload job = entries.get(index);
                int stopWidth = 48;
                int nameWidth = width - 11 - stopWidth;
                addRenderableWidget(Button.builder(jobLabel(job),
                        button -> minecraft.setScreenAndShow(new ScriptJobDetailScreen(this, job)))
                        .bounds(4, y, nameWidth, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.stop"),
                        button -> ClientNetworkController.stopJob(job.jobId()))
                        .bounds(7 + nameWidth, y, stopWidth, 18).build());
                y += 22;
            }
        }
    }

    private void buildFooter() {
        int footerY = height - 20;
        int pageSize = effectivePageSize(filter == Filter.LOCAL ? 76 : 62);
        int total = filter == Filter.LOCAL ? visibleLocalScripts().size()
                : filter == Filter.SHARED ? filteredShared().size() : ClientUiState.jobs().size();
        int maxPage = maxPage(total, pageSize);
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(4, footerY, 24, 16).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }).bounds(31, footerY, 24, 16).build());
        next.active = page < maxPage;
        addRenderableWidget(new StringWidget(60, footerY + 3, width - 130, 10,
                Component.translatable("screen.scriptautoce.status_bar",
                        page + 1, maxPage + 1, total).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 64, footerY, 60, 16).build());
    }

    private void createScript() {
        minecraft.setScreenAndShow(new ScriptNameScreen(this, false, name -> {
            String source = "meta name \"" + name.replace("\"", "_") + "\"\n\n"
                    + "log \"Hello from Script Auto CE\"\n";
            try {
                String id = ClientUiState.localRepository().createInFolder(currentFolder, name, source);
                ClientUiState.refreshLocal();
                minecraft.setScreenAndShow(new ScriptEditorScreen(this, id, source));
            } catch (RuntimeException exception) {
                ClientUiState.message(ClientUiState.MessageTone.ERROR, exception.getMessage());
                minecraft.setScreenAndShow(this);
            }
        }));
    }

    private void createFolder() {
        minecraft.setScreenAndShow(new ScriptNameScreen(this, true, name -> {
            try {
                currentFolder = ClientUiState.localRepository().createFolder(currentFolder, name);
                page = 0;
                ClientUiState.refreshLocal();
            } catch (RuntimeException exception) {
                ClientUiState.message(ClientUiState.MessageTone.ERROR, exception.getMessage());
            }
            minecraft.setScreenAndShow(this);
        }));
    }

    private void openEditor(String id) {
        minecraft.setScreenAndShow(new ScriptEditorScreen(this, id,
                ClientUiState.localRepository().load(id)));
    }

    private void openArguments(String id, String source) {
        var compilation = dev.scriptautoce.script.ScriptCompiler.compile(id, source);
        if (!compilation.success()) {
            ClientUiState.message(ClientUiState.MessageTone.ERROR,
                    compilation.diagnostics().getFirst().toString());
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

    private List<String> childFolders() {
        return ClientUiState.localRepository().folders().stream()
                .filter(folder -> parentOf(folder).equals(currentFolder))
                .toList();
    }

    private List<LocalScriptRepository.Summary> visibleLocalScripts() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        return ClientUiState.localScripts().stream()
                .filter(script -> !query.isBlank()
                        ? script.id().toLowerCase(Locale.ROOT).contains(query)
                                || script.title().toLowerCase(Locale.ROOT).contains(query)
                        : script.folder().equals(currentFolder))
                .toList();
    }

    private List<SharedScriptStore.Info> filteredShared() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        return ClientUiState.sharedScripts().stream()
                .filter(script -> query.isBlank()
                        || script.title().toLowerCase(Locale.ROOT).contains(query)
                        || script.ownerName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private String breadcrumb() {
        return currentFolder.isBlank() ? "ScriptAutoCE / Scripts" : "ScriptAutoCE / Scripts / "
                + currentFolder.replace("/", " / ");
    }

    private Component listHeader() {
        if (search.isBlank()) {
            return Component.literal(breadcrumb());
        }
        return Component.translatable("screen.scriptautoce.search_results",
                visibleLocalScripts().size(), search);
    }

    private int effectivePageSize(int startY) {
        return Math.max(1, (height - 24 - startY) / 22);
    }

    private static int maxPage(int total, int pageSize) {
        return Math.max(0, (total - 1) / pageSize);
    }

    private Component filterLabel() {
        return Component.translatable("screen.scriptautoce.filter." + filter.name().toLowerCase());
    }

    private String clip(String value, int pixelWidth) {
        return font.plainSubstrByWidth(value, Math.max(30, pixelWidth));
    }

    private Component jobLabel(JobEventPayload job) {
        return Component.translatable("screen.scriptautoce.job_entry",
                JobReasonDisplay.label(job.reasonCode()),
                job.progress() + "%",
                clip(JobReasonDisplay.description(job.reasonCode()).getString(), width - 140))
                .withStyle(JobReasonDisplay.color(job.reasonCode()));
    }

    private static String parentOf(String folder) {
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? "" : folder.substring(0, slash);
    }

    private static String folderName(String folder) {
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? folder : folder.substring(slash + 1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(0, 0, width, 30, 0xFF252526);
        graphics.fill(0, 30, width, 56, 0xFF2D2D30);
        if (filter == Filter.LOCAL) {
            graphics.fill(4, 60, 108, height - 24, 0xFF252526);
            graphics.fill(110, 60, width - 4, height - 24, 0xFF1E1E1E);
        } else {
            graphics.fill(4, 58, width - 4, height - 24, 0xFF1E1E1E);
        }
        graphics.fill(0, height - 20, width, height - 19, 0xFF3B82F6);
        graphics.fill(0, height - 19, width, height, 0xFF1F2937);
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
