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
        RUNNING,
        HISTORY
    }

    private static final int TAB_Y = 27;
    private static final int SEARCH_Y = 51;
    private static final int ACTION_Y = 75;
    private static final int LIST_HEADER_Y = 98;
    private static final int LIST_START_Y = 112;

    private final Screen parent;
    private Filter filter = Filter.LOCAL;
    private String currentFolder = "";
    private String search = "";
    private int page;
    private EditBox searchBox;
    private boolean restoreSearchFocus;
    private int restoreSearchCursor;

    public ScriptLibraryScreen(Screen parent) {
        super(Component.translatable("screen.scriptautoce.library"));
        this.parent = parent;
    }

    public void refreshFromData() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        clearWidgets();
        ClientUiState.setDataListener(this::refreshFromData);
        addRenderableWidget(new StringWidget(6, 7, 220, 10,
                Component.literal("SCRIPT AUTO CE").withStyle(ChatFormatting.BOLD), font));
        addRenderableWidget(new VSCodeButton(width - 92, 4, 60, 18,
                Component.translatable("screen.scriptautoce.settings"),
                button -> minecraft.setScreenAndShow(new ScriptSettingsScreen(this))));
        addRenderableWidget(new VSCodeButton(width - 28, 4, 24, 18,
                Component.literal("×"), button -> onClose()));

        addTabs();
        addSearch();
        addActionRow();

        if (filter == Filter.LOCAL) {
            buildLocalExplorer();
        } else {
            buildSharedOrJobs();
        }
        buildFooter();
    }

    private void addTabs() {
        int x = 4;
        for (Filter value : Filter.values()) {
            int width = switch (value) {
                case LOCAL, SHARED -> 62;
                case RUNNING -> 76;
                case HISTORY -> 68;
            };
            Filter target = value;
            addRenderableWidget(new VSCodeTabButton(x, TAB_Y, width, 18,
                    Component.translatable("screen.scriptautoce.filter." + value.name().toLowerCase()),
                    filter == value, button -> switchFilter(target)));
            x += width + 2;
        }
    }

    private void switchFilter(Filter next) {
        if (filter == next) {
            return;
        }
        filter = next;
        search = "";
        page = 0;
        restoreSearchFocus = false;
        if (filter == Filter.SHARED) {
            ClientNetworkController.requestShared("");
        }
        rebuildWidgets();
    }

    private void addSearch() {
        int searchWidth = filter == Filter.LOCAL ? Math.max(180, width - 212) : width - 8;
        searchBox = new EditBox(font, 4, SEARCH_Y, searchWidth, 18,
                Component.translatable("screen.scriptautoce.search"));
        searchBox.setHint(Component.translatable("screen.scriptautoce.search"));
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setValue(search);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        if (restoreSearchFocus) {
            setFocused(searchBox);
            searchBox.setFocused(true);
            searchBox.setCursorPosition(Math.min(restoreSearchCursor, searchBox.getValue().length()));
            restoreSearchFocus = false;
        }
        if (filter == Filter.LOCAL) {
            addRenderableWidget(new VSCodeButton(width - 204, SEARCH_Y, 94, 18,
                    Component.translatable("screen.scriptautoce.newFile"), button -> createScript()));
            addRenderableWidget(new VSCodeButton(width - 106, SEARCH_Y, 102, 18,
                    Component.translatable("screen.scriptautoce.newFolder"), button -> createFolder()));
        }
    }

    private void onSearchChanged(String value) {
        if (searchBox == null) {
            return;
        }
        boolean focused = searchBox.isFocused();
        int cursor = searchBox.getCursorPosition();
        search = value;
        page = 0;
        if (focused) {
            restoreSearchFocus = true;
            restoreSearchCursor = cursor;
        }
        rebuildWidgets();
    }

    private void addActionRow() {
        if (filter == Filter.LOCAL) {
            Button back = addRenderableWidget(new VSCodeButton(4, ACTION_Y, 24, 18,
                    Component.literal("<"), button -> {
                currentFolder = parentOf(currentFolder);
                page = 0;
                rebuildWidgets();
            }));
            back.active = !currentFolder.isBlank();
            addRenderableWidget(new StringWidget(34, ACTION_Y + 5, Math.max(100, width - 180), 10,
                    Component.literal(breadcrumb()).withStyle(ChatFormatting.GRAY), font));
        } else {
            Component summary = switch (filter) {
                case SHARED -> Component.translatable("screen.scriptautoce.shared_count_label",
                        filteredShared().size());
                case RUNNING -> Component.translatable("screen.scriptautoce.running_count_label",
                        filteredJobs().size());
                case HISTORY -> Component.translatable("screen.scriptautoce.history_count_label",
                        filteredJobs().size());
                default -> Component.empty();
            };
            addRenderableWidget(new StringWidget(6, ACTION_Y + 5, width - 90, 10,
                    summary.copy().withStyle(ChatFormatting.GRAY), font));
        }
        addRenderableWidget(new VSCodeButton(width - 66, ACTION_Y, 62, 18,
                Component.translatable("screen.scriptautoce.refresh"), button -> refresh()));
    }

    private void refresh() {
        ClientUiState.refreshLocal();
        if (filter == Filter.SHARED) {
            ClientNetworkController.requestShared(search);
        }
        rebuildWidgets();
    }

    private void buildLocalExplorer() {
        addRenderableWidget(new StringWidget(6, LIST_HEADER_Y, width - 12, 10,
                listHeader().copy().withStyle(ChatFormatting.GRAY), font));
        List<ExplorerEntry> entries = explorerEntries();
        int pageSize = effectivePageSize();
        int maxPage = maxPage(entries.size(), pageSize);
        page = Math.min(page, maxPage);
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = LIST_START_Y;
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
            addRenderableWidget(new StringWidget(8, LIST_START_Y + 4, width - 16, 10,
                    Component.translatable("screen.scriptautoce.empty").withStyle(ChatFormatting.GRAY),
                    font));
        }
    }

    private void buildSharedOrJobs() {
        addRenderableWidget(new StringWidget(6, LIST_HEADER_Y, width - 12, 10,
                listHeader().copy().withStyle(ChatFormatting.GRAY), font));
        int pageSize = effectivePageSize();
        if (filter == Filter.SHARED) {
            List<SharedScriptStore.Info> entries = filteredShared();
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int y = LIST_START_Y;
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
                addRenderableWidget(new VSCodeButton(7 + nameWidth + downloadWidth, y,
                        deleteWidth, 18,
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
                addRenderableWidget(new StringWidget(8, LIST_START_Y + 4, width - 16, 10,
                        Component.translatable("screen.scriptautoce.empty")
                                .withStyle(ChatFormatting.GRAY), font));
            }
            return;
        }

        List<JobEventPayload> entries = filteredJobs();
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = LIST_START_Y;
        boolean history = filter == Filter.HISTORY;
        for (int index = start; index < end; index++) {
            JobEventPayload job = entries.get(index);
            int actionWidth = history ? 48 : 52;
            int nameWidth = width - 11 - actionWidth;
            String title = JobReasonDisplay.label(job.reasonCode()).getString() + "  "
                    + shortId(job.jobId());
            String subtitle = history
                    ? JobTimeDisplay.rowTime(job)
                    : JobTimeDisplay.duration(job) + "  " + job.progress() + "%";
            addRenderableWidget(new ExplorerRowButton(4, y, nameWidth, 18,
                    Component.literal(title),
                    subtitle,
                    ExplorerRowButton.IconKind.JOB,
                    JobReasonDisplay.colorValue(job.reasonCode()),
                    button -> minecraft.setScreenAndShow(new ScriptJobDetailScreen(this, job))));
            if (history) {
                addRenderableWidget(new VSCodeButton(7 + nameWidth, y, actionWidth, 18,
                        Component.translatable("screen.scriptautoce.deleteRecord"),
                        button -> {
                            ClientUiState.removeJob(job.jobId());
                            rebuildWidgets();
                        }));
            } else {
                addRenderableWidget(new VSCodeButton(7 + nameWidth, y, actionWidth, 18,
                        Component.translatable("screen.scriptautoce.stop"),
                        button -> ClientNetworkController.stopJob(job.jobId())));
            }
            y += 21;
        }
        if (entries.isEmpty()) {
            Component message = Component.translatable(history
                    ? "screen.scriptautoce.emptyHistory"
                    : "screen.scriptautoce.emptyRunning");
            addRenderableWidget(new StringWidget(8, LIST_START_Y + 4, width - 16, 10,
                    message.copy().withStyle(ChatFormatting.GRAY), font));
        }
    }

    private void buildFooter() {
        int footerY = height - 20;
        int total = viewEntryCount();
        int pageSize = effectivePageSize();
        int maxPage = maxPage(total, pageSize);
        page = Math.min(page, maxPage);
        int statusX = 8;
        if (maxPage > 0) {
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
            statusX = 64;
        }
        addRenderableWidget(new StringWidget(statusX, footerY + 4, width - statusX - 10, 10,
                Component.translatable("screen.scriptautoce.status_bar",
                        page + 1, maxPage + 1, total).withStyle(ChatFormatting.GRAY), font));
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
                .filter(job -> filter == Filter.RUNNING
                        ? JobReasonDisplay.active(job.reasonCode())
                        : !JobReasonDisplay.active(job.reasonCode()))
                .filter(job -> query.isBlank()
                        || job.jobId().toLowerCase(Locale.ROOT).contains(query)
                        || JobReasonDisplay.description(job.reasonCode()).getString()
                                .toLowerCase(Locale.ROOT).contains(query)
                        || JobTimeDisplay.rowTime(job).toLowerCase(Locale.ROOT).contains(query))
                .sorted((left, right) -> Long.compare(right.startedAtMillis(), left.startedAtMillis()))
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
        return switch (filter) {
            case LOCAL -> Component.literal(breadcrumb());
            case SHARED -> Component.translatable("screen.scriptautoce.shared_header");
            case RUNNING -> Component.translatable("screen.scriptautoce.running_header");
            case HISTORY -> Component.translatable("screen.scriptautoce.history_header");
        };
    }

    private String breadcrumb() {
        return currentFolder.isBlank() ? "ScriptAutoCE / Scripts" : "ScriptAutoCE / Scripts / "
                + currentFolder.replace("/", " / ");
    }

    private int effectivePageSize() {
        return Math.max(1, (height - 24 - LIST_START_Y) / 21);
    }

    private static int maxPage(int total, int pageSize) {
        return Math.max(0, (total - 1) / pageSize);
    }

    private String clip(String value, int pixelWidth) {
        return font.plainSubstrByWidth(value, Math.max(30, pixelWidth));
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? id : id.substring(0, 8);
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
        graphics.fill(0, 25, width, TAB_Y + 20, 0xFF2D2D30);
        graphics.fill(4, 48, width - 4, 73, 0xFF252526);
        graphics.fill(4, 73, width - 4, 95, 0xFF2D2D30);
        graphics.fill(4, 95, width - 4, height - 24, 0xFF1E1E1E);
        graphics.fill(0, height - 20, width, height - 19, 0xFF3B82F6);
        graphics.fill(0, height - 19, width, height, 0xFF1F2937);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientUiState.clearDataListener();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        ClientUiState.clearDataListener();
        super.removed();
    }

    private record ExplorerEntry(String folder, LocalScriptRepository.Summary script) {
    }
}
