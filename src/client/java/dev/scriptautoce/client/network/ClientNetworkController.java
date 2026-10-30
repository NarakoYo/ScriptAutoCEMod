package dev.scriptautoce.client.network;

import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import dev.scriptautoce.ExecutionMode;
import dev.scriptautoce.ScriptAutoCe;
import dev.scriptautoce.client.script.DirectScriptJobManager;
import dev.scriptautoce.client.ui.ClientUiState;
import dev.scriptautoce.client.ui.ScriptLibraryScreen;
import dev.scriptautoce.network.ScriptPayloads;
import dev.scriptautoce.network.ScriptPayloads.ActionResultPayload;
import dev.scriptautoce.network.ScriptPayloads.CancelJobPayload;
import dev.scriptautoce.network.ScriptPayloads.CatalogPayload;
import dev.scriptautoce.network.ScriptPayloads.DeleteSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.DownloadSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.ErrorPayload;
import dev.scriptautoce.network.ScriptPayloads.GetServerSettingsPayload;
import dev.scriptautoce.network.ScriptPayloads.HelloAckPayload;
import dev.scriptautoce.network.ScriptPayloads.HelloPayload;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.network.ScriptPayloads.ListSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.RunScriptPayload;
import dev.scriptautoce.network.ScriptPayloads.ScriptContentPayload;
import dev.scriptautoce.network.ScriptPayloads.ServerSettingsPayload;
import dev.scriptautoce.network.ScriptPayloads.UploadSharedPayload;
import dev.scriptautoce.storage.JsonFiles;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientNetworkController {
    private static final Type SHARED_LIST_TYPE = new TypeToken<List<SharedScriptStore.Info>>() {
    }.getType();
    private static final Map<String, PendingRun> PENDING_RUNS = new HashMap<>();
    private static final Map<String, String> METADATA_BY_DOWNLOAD = new HashMap<>();
    private static Minecraft client;
    private static DirectScriptJobManager directJobs;
    private static KeyMapping openLibrary;

    private ClientNetworkController() {
    }

    public static void initialize() {
        client = Minecraft.getInstance();
        directJobs = new DirectScriptJobManager(client);
        registerReceivers();
        registerEvents();
    }

    public static DirectScriptJobManager directJobs() {
        return directJobs;
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HelloAckPayload.TYPE, (payload, context) -> {
            ClientUiState.setServerAvailable(payload.protocolVersion() == ScriptPayloads.PROTOCOL_VERSION);
            ClientUiState.setServerLimits(payload.maxSourceBytes());
            ClientUiState.setSharingEnabled(payload.sharingEnabled());
            ClientUiState.setServerSettingsJson(payload.settingsJson());
            ClientUiState.message("Server validation available");
            requestShared("");
        });
        ClientPlayNetworking.registerGlobalReceiver(CatalogPayload.TYPE, (payload, context) -> {
            List<SharedScriptStore.Info> entries = JsonFiles.GSON.fromJson(payload.entriesJson(), SHARED_LIST_TYPE);
            ClientUiState.setSharedScripts(entries == null ? List.of() : entries);
        });
        ClientPlayNetworking.registerGlobalReceiver(ScriptContentPayload.TYPE, (payload, context) -> {
            if (payload.error() != null && !payload.error().isBlank()) {
                ClientUiState.message(payload.error());
                return;
            }
            ClientUiState.localRepository().importSource(payload.id(), payload.source());
            ClientUiState.refreshLocal();
            PendingRun pending = PENDING_RUNS.remove(payload.id());
            if (pending != null) {
                runSource(payload.source(), pending.arguments());
            } else {
                ClientUiState.message("Downloaded " + payload.id());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ActionResultPayload.TYPE, (payload, context) -> {
            if (payload.success() && payload.action().equals("upload")) {
                try {
                    SharedScriptStore.Info info = JsonFiles.GSON.fromJson(
                            payload.message(), SharedScriptStore.Info.class);
                    ClientUiState.message("Shared " + info.title() + " r" + info.revision());
                } catch (RuntimeException exception) {
                    ClientUiState.message("Shared script uploaded");
                }
                requestShared("");
            } else {
                ClientUiState.message(payload.message());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(JobEventPayload.TYPE, (payload, context) ->
                ClientUiState.updateJob(payload));
        ClientPlayNetworking.registerGlobalReceiver(ErrorPayload.TYPE, (payload, context) ->
                ClientUiState.message(payload.message() + (payload.line() > 0
                        ? " at " + payload.line() + ":" + payload.column() : "")));
        ClientPlayNetworking.registerGlobalReceiver(ServerSettingsPayload.TYPE, (payload, context) ->
                ClientUiState.setServerSettingsJson(payload.settingsJson()));
    }

    private static void registerEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraftClient) -> {
            ClientUiState.resetConnection();
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload(ScriptPayloads.PROTOCOL_VERSION));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraftClient) -> {
            directJobs.cancelAll();
            ClientUiState.resetConnection();
            PENDING_RUNS.clear();
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(ClientNetworkController::handleChatCommand);
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ScriptAutoCe.MOD_ID, "script_ui"));
        openLibrary = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.scriptautoce.library", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            directJobs.tick();
            while (openLibrary.consumeClick()) {
                minecraftClient.setScreenAndShow(new ScriptLibraryScreen(null));
            }
        });
    }

    public static void runLocal(String id, Map<String, String> arguments) {
        LocalScriptRepository.Summary summary = ClientUiState.localScripts().stream()
                .filter(script -> script.id().equals(id)).findFirst().orElse(null);
        if (summary == null) {
            SharedScriptStore.Info shared = findShared(id);
            if (shared != null) {
                PENDING_RUNS.put(shared.id(), new PendingRun(arguments));
                downloadShared(shared.id());
            } else {
                ClientUiState.message("Script not found: " + id);
            }
            return;
        }
        String source = ClientUiState.localRepository().load(summary.id());
        runSource(source, arguments);
    }

    public static void runSource(String source, Map<String, String> arguments) {
        ExecutionMode mode = effectiveMode();
        try {
            if (mode == ExecutionMode.SERVER) {
                String id = UUID.randomUUID().toString();
                ClientPlayNetworking.send(new RunScriptPayload(id, source,
                        JsonFiles.GSON.toJson(arguments)));
                ClientUiState.message("Sent script to the server for validation. Use ##status to track it.");
            } else {
                directJobs.start(source, arguments);
                ClientUiState.message("Started in direct command mode. Use ##status to track it.");
            }
        } catch (RuntimeException exception) {
            ClientUiState.message(exception.getMessage());
        }
    }

    public static void stopJob(String id) {
        if (directJobs.cancel(id)) {
            ClientUiState.message("Direct job cancelled");
        } else if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new CancelJobPayload(id));
        }
    }

    public static void share(String localId) {
        if (!ClientUiState.serverAvailable() || !ClientUiState.sharingEnabled()) {
            ClientUiState.message("The server shared library is unavailable");
            return;
        }
        try {
            String source = ClientUiState.localRepository().load(localId);
            String playerId = client.player == null ? "" : client.player.getUUID().toString();
            boolean ownsShared = ClientUiState.sharedScripts().stream()
                    .anyMatch(info -> info.id().equals(localId) && info.ownerUuid().equals(playerId));
            ClientPlayNetworking.send(new UploadSharedPayload(ownsShared ? localId : "", source));
        } catch (RuntimeException exception) {
            ClientUiState.message(exception.getMessage());
        }
    }

    public static void deleteShared(String id) {
        if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new DeleteSharedPayload(id));
        }
    }

    public static void downloadShared(String id) {
        if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new DownloadSharedPayload(id));
        } else {
            ClientUiState.message("The server shared library is unavailable");
        }
    }

    public static void requestShared(String query) {
        if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new ListSharedPayload(query, 0, 100));
        }
    }

    public static void requestServerSettings() {
        if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(GetServerSettingsPayload.INSTANCE);
        }
    }

    public static void updateServerSettings(String json) {
        if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new ScriptPayloads.UpdateServerSettingsPayload(json));
        }
    }

    public static ExecutionMode effectiveMode() {
        ExecutionMode configured = ClientUiState.settings().executionMode();
        if (configured == ExecutionMode.DIRECT) {
            return ExecutionMode.DIRECT;
        }
        if (configured == ExecutionMode.SERVER) {
            return ClientUiState.serverAvailable() ? ExecutionMode.SERVER : ExecutionMode.DIRECT;
        }
        return ClientUiState.serverAvailable() ? ExecutionMode.SERVER : ExecutionMode.DIRECT;
    }

    private static boolean handleChatCommand(String message) {
        if (message == null || !message.startsWith("##")) {
            return true;
        }
        String body = message.substring(2).strip();
        if (body.isBlank()) {
            help();
            return false;
        }
        String[] parts = body.split("\\s+", 2);
        String command = parts.length == 0 ? "help" : parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].strip() : "";
        switch (command) {
            case "list" -> listScripts();
            case "run" -> runChat(rest);
            case "stop" -> stopFromChat(rest);
            case "status" -> status();
            case "share" -> share(rest);
            case "download" -> downloadShared(rest);
            case "help" -> help();
            default -> ClientUiState.message("Unknown command. Use ##help");
        }
        return false;
    }

    private static void listScripts() {
        ClientUiState.refreshLocal();
        ClientUiState.message("Local scripts: " + ClientUiState.localScripts().size());
        ClientUiState.localScripts().stream().limit(30).forEach(script ->
                ClientUiState.message(" - " + script.id() + " : " + script.title()));
        if (ClientUiState.serverAvailable()) {
            ClientUiState.message("Shared scripts: " + ClientUiState.sharedScripts().size());
            requestShared("");
        }
    }

    private static void runChat(String rest) {
        if (rest.isBlank()) {
            ClientUiState.message("Usage: ##run <name> [key=value...]");
            return;
        }
        String[] values = rest.split("\\s+");
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int index = 1; index < values.length; index++) {
            int separator = values[index].indexOf('=');
            if (separator > 0) {
                arguments.put(values[index].substring(0, separator), values[index].substring(separator + 1));
            }
        }
        runLocal(values[0], arguments);
    }

    private static void stopFromChat(String id) {
        if (!id.isBlank()) {
            stopJob(id);
            return;
        }
        ClientUiState.jobs().keySet().forEach(ClientNetworkController::stopJob);
    }

    private static void status() {
        if (ClientUiState.jobs().isEmpty()) {
            ClientUiState.message("No running jobs");
            return;
        }
        ClientUiState.jobs().values().forEach(job -> ClientUiState.message(job.jobId() + " "
                + job.eventType() + " " + job.progress() + "% " + job.message()));
    }

    private static void help() {
        ClientUiState.message("Commands: ##list | ##run <name> [key=value...] | ##stop [job-id] "
                + "| ##status | ##share <local-id> | ##download <shared-id> | ##help");
    }

    private static SharedScriptStore.Info findShared(String name) {
        return ClientUiState.sharedScripts().stream()
                .filter(info -> info.id().equals(name) || info.title().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private record PendingRun(Map<String, String> arguments) {
        private PendingRun {
            arguments = Map.copyOf(arguments);
        }
    }
}
