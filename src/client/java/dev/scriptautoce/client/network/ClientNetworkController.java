package dev.scriptautoce.client.network;

import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import dev.scriptautoce.ExecutionMode;
import dev.scriptautoce.ScriptAutoCe;
import dev.scriptautoce.client.script.DirectScriptJobManager;
import dev.scriptautoce.client.ui.ClientUiState;
import dev.scriptautoce.client.ui.ClientUiState.MessageTone;
import dev.scriptautoce.client.ui.JobReasonDisplay;
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

    public static KeyMapping openLibraryKey() {
        if (openLibrary == null) {
            throw new IllegalStateException("Script Auto CE key mapping is not initialized");
        }
        return openLibrary;
    }

    public static void setOpenLibraryKey(InputConstants.Key key) {
        openLibraryKey().setKey(key);
        client.options.save();
    }

    public static void resetOpenLibraryKey() {
        setOpenLibraryKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HelloAckPayload.TYPE, (payload, context) -> {
            ClientUiState.setServerAvailable(payload.protocolVersion() == ScriptPayloads.PROTOCOL_VERSION);
            ClientUiState.setServerLimits(payload.maxSourceBytes());
            ClientUiState.setSharingEnabled(payload.sharingEnabled());
            ClientUiState.setServerSettingsJson(payload.settingsJson());
            ClientUiState.message(MessageTone.SUCCESS,
                    Component.translatable("message.scriptautoce.server_ready"));
            requestShared("");
        });
        ClientPlayNetworking.registerGlobalReceiver(CatalogPayload.TYPE, (payload, context) -> {
            List<SharedScriptStore.Info> entries = JsonFiles.GSON.fromJson(payload.entriesJson(), SHARED_LIST_TYPE);
            ClientUiState.setSharedScripts(entries == null ? List.of() : entries);
        });
        ClientPlayNetworking.registerGlobalReceiver(ScriptContentPayload.TYPE, (payload, context) -> {
            if (payload.error() != null && !payload.error().isBlank()) {
                ClientUiState.message(MessageTone.ERROR, Component.literal(payload.error()));
                return;
            }
            ClientUiState.localRepository().importSource(payload.id(), payload.source());
            ClientUiState.refreshLocal();
            PendingRun pending = PENDING_RUNS.remove(payload.id());
            if (pending != null) {
                runSource(payload.source(), pending.arguments());
            } else {
                ClientUiState.message(MessageTone.SUCCESS,
                        Component.translatable("message.scriptautoce.downloaded", payload.id()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ActionResultPayload.TYPE, (payload, context) -> {
            if (payload.success() && payload.action().equals("upload")) {
                try {
                    SharedScriptStore.Info info = JsonFiles.GSON.fromJson(
                            payload.message(), SharedScriptStore.Info.class);
                    ClientUiState.message(MessageTone.SUCCESS,
                            Component.translatable("message.scriptautoce.shared",
                                    info.title(), info.revision()));
                } catch (RuntimeException exception) {
                    ClientUiState.message(MessageTone.SUCCESS,
                            Component.translatable("message.scriptautoce.uploaded"));
                }
                requestShared("");
            } else {
                if (payload.success()) {
                    ClientUiState.message(MessageTone.SUCCESS,
                            Component.translatable("message.scriptautoce.action_success." + payload.action()));
                } else {
                    ClientUiState.message(MessageTone.ERROR,
                            Component.translatable("message.scriptautoce.action_failed",
                                    payload.message()));
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(JobEventPayload.TYPE, (payload, context) -> {
            ClientUiState.updateJob(payload);
            for (String log : ClientUiState.consumeNewLogs(payload)) {
                ClientUiState.message(MessageTone.MUTED, log);
            }
            switch (payload.eventType()) {
                case "started" -> ClientUiState.message(MessageTone.INFO,
                        Component.translatable("message.scriptautoce.job_started", payload.jobId()));
                case "finished" -> ClientUiState.message(MessageTone.SUCCESS,
                        Component.translatable("message.scriptautoce.job_finished"));
                case "failed", "cancelled" -> {
                    Component reason = JobReasonDisplay.label(payload.reasonCode());
                    MessageTone tone = JobReasonDisplay.color(payload.reasonCode()) == net.minecraft.ChatFormatting.RED
                            ? MessageTone.ERROR : MessageTone.WARNING;
                    ClientUiState.message(tone,
                            Component.translatable("message.scriptautoce.job_interrupted", reason,
                                    JobReasonDisplay.description(payload.reasonCode())));
                }
                default -> {
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ErrorPayload.TYPE, (payload, context) ->
                ClientUiState.message(MessageTone.ERROR,
                        Component.translatable("message.scriptautoce.server_error",
                                payload.message(), payload.line(), payload.column())));
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
                ClientUiState.message(MessageTone.ERROR,
                        Component.translatable("message.scriptautoce.not_found", id));
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
                ClientUiState.message(MessageTone.SUCCESS,
                        Component.translatable("message.scriptautoce.sent_server"));
            } else {
                directJobs.start(source, arguments);
                ClientUiState.message(MessageTone.WARNING,
                        Component.translatable("message.scriptautoce.direct_started"));
            }
        } catch (RuntimeException exception) {
            ClientUiState.message(MessageTone.ERROR, Component.literal(exception.getMessage()));
        }
    }

    public static void stopJob(String id) {
        if (directJobs.cancel(id)) {
            ClientUiState.message(MessageTone.WARNING,
                    Component.translatable("message.scriptautoce.direct_cancelled"));
        } else if (ClientUiState.serverAvailable()) {
            ClientPlayNetworking.send(new CancelJobPayload(id));
        }
    }

    public static void share(String localId) {
        if (!ClientUiState.serverAvailable() || !ClientUiState.sharingEnabled()) {
            ClientUiState.message(MessageTone.WARNING,
                    Component.translatable("message.scriptautoce.shared_unavailable"));
            return;
        }
        try {
            String source = ClientUiState.localRepository().load(localId);
            String playerId = client.player == null ? "" : client.player.getUUID().toString();
            boolean ownsShared = ClientUiState.sharedScripts().stream()
                    .anyMatch(info -> info.id().equals(localId) && info.ownerUuid().equals(playerId));
            ClientPlayNetworking.send(new UploadSharedPayload(ownsShared ? localId : "", source));
        } catch (RuntimeException exception) {
            ClientUiState.message(MessageTone.ERROR, Component.literal(exception.getMessage()));
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
            ClientUiState.message(MessageTone.WARNING,
                    Component.translatable("message.scriptautoce.shared_unavailable"));
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
        try {
            switch (command) {
                case "list" -> listScripts();
                case "run" -> runChat(rest);
                case "stop" -> stopFromChat(rest.isBlank() ? "" : parseFirstArgument(rest).value());
                case "status" -> status();
                case "share" -> {
                    if (rest.isBlank()) {
                        ClientUiState.message(MessageTone.WARNING,
                                Component.translatable("message.scriptautoce.usage_share"));
                    } else {
                        share(parseFirstArgument(rest).value());
                    }
                }
                case "download" -> {
                    if (rest.isBlank()) {
                        ClientUiState.message(MessageTone.WARNING,
                                Component.translatable("message.scriptautoce.usage_download"));
                    } else {
                        downloadShared(parseFirstArgument(rest).value());
                    }
                }
                case "help" -> help();
                default -> ClientUiState.message(MessageTone.ERROR,
                        Component.translatable("message.scriptautoce.unknown_command"));
            }
        } catch (RuntimeException exception) {
            ClientUiState.message(MessageTone.ERROR, Component.literal(exception.getMessage()));
        }
        return false;
    }

    private static void listScripts() {
        ClientUiState.refreshLocal();
        ClientUiState.message(MessageTone.INFO,
                Component.translatable("message.scriptautoce.local_count",
                        ClientUiState.localScripts().size()));
        ClientUiState.localScripts().stream().limit(30).forEach(script ->
                ClientUiState.message(MessageTone.MUTED,
                        Component.translatable("message.scriptautoce.list_item",
                                script.id(), script.title())));
        if (ClientUiState.serverAvailable()) {
            ClientUiState.message(MessageTone.INFO,
                    Component.translatable("message.scriptautoce.shared_count",
                            ClientUiState.sharedScripts().size()));
            requestShared("");
        }
    }

    private static void runChat(String rest) {
        if (rest.isBlank()) {
            ClientUiState.message(MessageTone.WARNING,
                    Component.translatable("message.scriptautoce.usage_run"));
            return;
        }
        FirstArgument first = parseFirstArgument(rest);
        String[] values = first.rest().isBlank() ? new String[0] : first.rest().split("\\s+");
        Map<String, String> arguments = new LinkedHashMap<>();
        for (String value : values) {
            int separator = value.indexOf('=');
            if (separator > 0) {
                arguments.put(value.substring(0, separator), value.substring(separator + 1));
            }
        }
        runLocal(first.value(), arguments);
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
            ClientUiState.message(MessageTone.MUTED,
                    Component.translatable("message.scriptautoce.no_jobs"));
            return;
        }
        ClientUiState.jobs().values().forEach(job -> ClientUiState.message(
                job.eventType().equals("failed") ? MessageTone.ERROR
                        : job.eventType().equals("cancelled") ? MessageTone.WARNING : MessageTone.INFO,
                Component.translatable("message.scriptautoce.job_status",
                        job.jobId(),
                        JobReasonDisplay.label(job.reasonCode()),
                        job.progress(), job.message())));
    }

    private static void help() {
        ClientUiState.message(MessageTone.MUTED,
                Component.translatable("message.scriptautoce.help"));
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

    private static FirstArgument parseFirstArgument(String input) {
        String value = input.strip();
        if (!value.startsWith("\"")) {
            int space = value.indexOf(' ');
            return space < 0 ? new FirstArgument(value, "")
                    : new FirstArgument(value.substring(0, space), value.substring(space + 1).strip());
        }
        StringBuilder parsed = new StringBuilder();
        boolean escaped = false;
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                parsed.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return new FirstArgument(parsed.toString(), value.substring(index + 1).strip());
            } else {
                parsed.append(character);
            }
        }
        throw new IllegalArgumentException("Unclosed quoted script name");
    }

    private record FirstArgument(String value, String rest) {
    }
}
