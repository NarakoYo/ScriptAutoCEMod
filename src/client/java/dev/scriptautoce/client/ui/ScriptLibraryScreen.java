package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.ArrayList;
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

    public ScriptLibraryScreen(Screen parent) {
        super(Component.translatable("screen.scriptautoce.library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(new StringWidget(6, 7, 120, 10,
                Component.literal("SCRIPT AUTO CE").withStyle(ChatFormatting.BOLD), font));
        addRenderableWidget(new VSCodeButton(width - 66, 4, 62, 18,
                Component.translatable("screen.scriptautoce.settings"),
                button -> minecraft.setScreenAndShow(new ScriptSettingsScreen(this))));

        addRenderableWidget(new VSCodeButton(4, 27, 58, 18, filterLabel(), button -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            search = "";
            page = 0;
            if (filter == Filter.SHARED) {
                ClientNetworkController.requestShared(search);
            }
            rebuildWidgets();
        }));

        Button back = addRenderableWidget(new VSCodeButton(66, 27, 24, 18, Component.literal("<"), button -> {
            currentFolder = parentOf(currentFolder);
            page = 0;
            rebuildWidgets();
        }));
        back.active = !currentFolder.isBlank();
        addRenderableWidget(new StringWidget(96, 31, Math.max(80, width - 168), 10,
                Component.literal(breadcrumb()).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new VSCodeButton(width - 66, 27, 62, 18,
                Component.translatable("screen.scriptautoce.refresh"), button -> {
            ClientUiState.refreshLocal();
            ClientNetworkController.requestShared(search);
            rebuildWidgets();
        }));

        int searchWidth = filter == Filter.LOCAL ? Math.max(120, width - 218) : width - 8;
        EditBox searchBox = new EditBox(font, 4, 51, searchWidth, 18,
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
            addRenderableWidget(new StringWidget(8, 56, 150, 10,
                    Component.translatable("screen.scriptautoce.search").withStyle(ChatFormatting.GRAY), font));
        }
        if (filter == Filter.LOCAL) {
            addRenderableWidget(new VSCodeButton(width - 210, 51, 64, 18,
                    Component.translatable("screen.scriptautoce.newFile"), button -> createScript()));
            addRenderableWidget(new VSCodeButton(width - 142, 51, 68, 18,
                    Component.translatable("screen.scriptautoce.newFolder"), button -> createFolder()));
        }

        if (filter == Filter.LOCAL) {
            buildLocalExplorer();
        } else {
            buildSharedOrRunning();
        }
        buildFooter();
    }

    private void buildLocalExplorer() {
        addRenderableWidget(new StringWidget(6, 77, width - 12, 10,
                listHeader().copy().withStyle(ChatFormatting.GRAY), font));
        List<ExplorerEntry> entries = explorerEntries();
        int pageSize = effectivePageSize(94);
        int maxPage = maxPage(entries.size(), pageSize);
        page = Math.min(page, maxPage);
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = 94;
        for (int index = start; index < end; index++) {
            ExplorerEntry entry = entries.get(index);
            if (entry.folder() != null) {
                String name = folderName(entry.folder());
                addRenderableWidget(new ExplorerRowButton(4, y, width - 8, 18,
                        Component.literal(name),
                        Component.translatable("screen.scriptautoce.folder").getString(),
                        ExplorerRowButton.IconKind.FOLDER, 0xFFD7BA7D,
                        button -> {
                            currentFolder = entry.folder();
                            page = 0;
                            rebuildWidgets();
                        }));
            } else {
                LocalScriptRepository.Summary script = entry.script();
                int runWidth = 42;
                int shareWidth = 42;
                int nameWidth = width - 8 - runWidth - shareWidth - 6;
                String displayName = search.isBlank() ? script.fileName() : script.relativePath();
                addRenderableWidget(new ExplorerRowButton(4, y, nameWidth, 18,
                        Component.literal(clip(displayName, nameWidth - 8)),
                        "SACE",
                        ExplorerRowButton.IconKind.FILE,
                        0xFF75BEFF,
                        button -> openEditor(script.id())));
                addRenderableWidget(new VSCodeButton(7 + nameWidth, y, runWidth, 18,
                        Component.translatable("screen.scriptautoce.run"),
                        button -> openArguments(script.id(),
                                ClientUiState.localRepository().load(script.id()))));
                addRenderableWidget(new VSCodeButton(10 + nameWidth + runWidth, y, shareWidth, 18,
                        Component.translatable("screen.scriptautoce.share"),
                        button -> ClientNetworkController.share(script.id())));
            }
            y += 21;
        }
        if (entries.isEmpty()) {
            addRenderableWidget(new StringWidget(8, 98, width - 16, 10,
                    Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY), font));
        }
    }

    private void buildSharedOrRunning() {
        int pageSize = effectivePageSize(78);
        if (filter == Filter.SHARED) {
            List<SharedScriptStore.Info> entries = filteredShared();
            int maxPage = maxPage(entries.size(), pageSize);
            page = Math.min(page, maxPage);
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int y = 78;
            for (int index = start; index < end; index++) {
                SharedScriptStore.Info shared = entries.get(index);
                int downloadWidth = 64;
                int deleteWidth = 44;
                int nameWidth = width - 12 - downloadWidth - deleteWidth;
                addRenderableWidget(new ExplorerRowButton(4, y, nameWidth, 18,
                        Component.literal(shared.title()),
                        Component.translatable("screen.scriptautoce.shared_subtitle",
                                shared.ownerName(), shared.revision()).getString(),
                        ExplorerRowButton.IconKind.SHARED,
                        0xFF4EC9B0,
                        button -> ClientNetworkController.previewShared(shared.id(), this)));
                addRenderableWidget(new VSCodeButton(7 + nameWidth, y, downloadWidth, 18,
                        Component.translatable("screen.scriptautoce.download"),
                        button -> ClientNetworkController.downloadShared(shared.id())));
                addRenderableWidget(new VSCodeButton(7 + nameWidth + downloadWidth, y, deleteWidth, 18,
                        Component.translatable("selectServer.delete"),
                        button -> minecraft.setScreenAndShow(new ScriptConfirmScreen(this,
                                Component.translatable("screen.scriptautoce.delete.title"),
                                Component.translatable("screen.scriptautoce.delete.message"),
                                () -> {
                                    ClientNetworkController.deleteShared(shared.id());
                                    ScriptLibraryScreen refreshed = new ScriptLibraryScreen(parent);
                                    refreshed.filter = Filter.SHARED;
                                    refreshed.search = search;
                                    minecraft.setScreenAndShow(refreshed);
                                }))));
                y += 21;
            }
            if (entries.isEmpty()) {
                addRenderableWidget(new StringWidget(8, 82, width - 16, 10,
                        Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY), font));
            }
        } else {
            List<JobEventPayload> entries = filteredJobs();
            int maxPage = maxPage(entries.size(), pageSize);
            page = Math.min(page, maxPage);
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int y = 78;
            for (int index = start; index < end; index++) {
                JobEventPayload job = entries.get(index);
                int stopWidth = 48;
                int nameWidth = width - 11 - stopWidth;
                addRenderableWidget(new ExplorerRowButton(4, y, nameWidth, 18,
                        Component.literal(JobReasonDisplay.label(job.reasonCode()).getString() + " · "
                                + JobReasonDisplay.description(job.reasonCode()).getString()),
                        job.progress() + "%",
                        ExplorerRowButton.IconKind.JOB,
                        JobReasonDisplay.colorValue(job.reasonCode()),
                        button -> minecraft.setScreenAndShow(new ScriptJobDetailScreen(this, job))));
                addRenderableWidget(new VSCodeButton(7 + nameWidth, y, stopWidth, 18,
                        Component.translatable("screen.scriptautoce.stop"),
                        button -> ClientNetworkController.stopJob(job.jobId())));
                y += 21;
            }
            if (entries.isEmpty()) {
                addRenderableWidget(new StringWidget(8, 82, width - 16, 10,
                        Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY), font));
            }
        }
    }

    private void buildFooter() {
        int footerY = height - 20;
        int total = viewEntryCount();
        int pageSize = effectivePageSize(filter == Filter.LOCAL ? 94 : 78);
        int maxPage = maxPage(total, pageSize);
        Button previous = addRenderableWidget(new VSCodeButton(4, footerY, 24, 16,
                Component.literal("<"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }));
        previous.active = page > 0;
        Button next = addRenderableWidget(new VSCodeButton(31, footerY, 24, 16,
                Component.literal(">"), button -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }));
        next.active = page < maxPage;
        addRenderableWidget(new StringWidget(60, footerY + 3, width - 130, 10,
                Component.translatable("screen.scriptautoce.status_bar",
                        page + 1, maxPage + 1, total).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new VSCodeButton(width - 64, footerY, 60, 16,
                Component.translatable("gui.done"), button -> onClose()));
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
                minecraft.setScreenAndShow(new ScriptConfirmScreen(this, () -> {
                    action.run();
                    minecraft.setScreenAndShow(this);
                }));
            } else {
                action.run();
            }
        } else {
            minecraft.setScreenAndShow(new ScriptArgumentsScreen(this, id, source,
                    compilation.script().arguments()));
        }
    }

    private List<ExplorerEntry> explorerEntries() {
        List<ExplorerEntry> result = new ArrayList<>();
        String query = search.strip().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            ClientUiState.localRepository().folders().stream()
                    .filter(folder -> parentOf(folder).equals(currentFolder))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(folder -> result.add(new ExplorerEntry(folder, null)));
        }
        ClientUiState.localScripts().stream()
                .filter(script -> query.isBlank()
                        ? script.folder().equals(currentFolder)
                        : script.id().toLowerCase(Locale.ROOT).contains(query)
                                || script.title().toLowerCase(Locale.ROOT).contains(query))
                .sorted((left, right) -> left.fileName().compareToIgnoreCase(right.fileName()))
                .forEach(script -> result.add(new ExplorerEntry(null, script)));
        return result;
    }

    private List<SharedScriptStore.Info> filteredShared() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        return ClientUiState.sharedScripts().stream()
                .filter(script -> query.isBlank()
                        || script.title().toLowerCase(Locale.ROOT).contains(query)
                        || script.ownerName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<JobEventPayload> filteredJobs() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        return ClientUiState.jobs().values().stream()
                .filter(job -> query.isBlank()
                        || job.jobId().toLowerCase(Locale.ROOT).contains(query)
                        || JobReasonDisplay.description(job.reasonCode()).getString()
                                .toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private int viewEntryCount() {
        return filter == Filter.LOCAL ? explorerEntries().size()
                : filter == Filter.SHARED ? filteredShared().size() : filteredJobs().size();
    }

    private Component listHeader() {
        if (!search.isBlank()) {
            return Component.translatable("screen.scriptautoce.search_results",
                    viewEntryCount(), search);
        }
        return Component.literal(breadcrumb());
    }

    private String breadcrumb() {
        return currentFolder.isBlank() ? "ScriptAutoCE / Scripts" : "ScriptAutoCE / Scripts / "
                + currentFolder.replace("/", " / ");
    }

    private int effectivePageSize(int startY) {
        return Math.max(1, (height - 24 - startY) / 21);
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
                clip(JobReasonDisplay.description(job.reasonCode()).getString(), width - 150))
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
        graphics.fill(0, 0, width, 25, 0xFF252526);
        graphics.fill(0, 25, width, 73, 0xFF2D2D30);
        graphics.fill(4, 76, width - 4, height - 24, 0xFF1E1E1E);
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

    private record ExplorerEntry(String folder, LocalScriptRepository.Summary script) {
    }
}
