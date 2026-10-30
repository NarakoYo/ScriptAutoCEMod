package dev.scriptautoce.server;

import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.network.ScriptPayloads.ActionResultPayload;
import dev.scriptautoce.network.ScriptPayloads.CatalogPayload;
import dev.scriptautoce.network.ScriptPayloads.CancelJobPayload;
import dev.scriptautoce.network.ScriptPayloads.DeleteSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.DownloadSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.ErrorPayload;
import dev.scriptautoce.network.ScriptPayloads.GetJobPayload;
import dev.scriptautoce.network.ScriptPayloads.HelloAckPayload;
import dev.scriptautoce.network.ScriptPayloads.HelloPayload;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.network.ScriptPayloads.ListSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.RunScriptPayload;
import dev.scriptautoce.network.ScriptPayloads.ScriptContentPayload;
import dev.scriptautoce.network.ScriptPayloads.ServerSettingsPayload;
import dev.scriptautoce.network.ScriptPayloads.UploadSharedPayload;
import dev.scriptautoce.network.ScriptPayloads.UpdateServerSettingsPayload;
import dev.scriptautoce.network.ScriptPayloads;
import dev.scriptautoce.storage.JsonFiles;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ServerNetworkController {
    private ServerNetworkController() {
    }

    public static void hello(ServerPlayer player, HelloPayload payload) {
        ServerRuntime runtime = ServerRuntime.get();
        if (payload.protocolVersion() != ScriptPayloads.PROTOCOL_VERSION) {
            send(player, new ErrorPayload("protocol", "Protocol version mismatch", 0, 0));
            return;
        }
        ServerSettings settings = runtime.settings();
        send(player, new HelloAckPayload(ScriptPayloads.PROTOCOL_VERSION, settings.maxSourceBytes(),
                settings.maxInstructions(), settings.sharingEnabled(), JsonFiles.GSON.toJson(settings)));
    }

    public static void listShared(ServerPlayer player, ListSharedPayload payload) {
        ServerRuntime runtime = ServerRuntime.get();
        if (!runtime.settings().sharingEnabled()) {
            send(player, new ActionResultPayload(false, "list", "Script sharing is disabled"));
            return;
        }
        List<SharedScriptStore.Info> all = runtime.sharedScripts().list(payload.query());
        int offset = Math.min(payload.offset(), all.size());
        int limit = Math.max(1, Math.min(100, payload.limit()));
        List<SharedScriptStore.Info> page = all.subList(offset, Math.min(all.size(), offset + limit));
        send(player, new CatalogPayload(JsonFiles.GSON.toJson(page), all.size()));
    }

    public static void download(ServerPlayer player, DownloadSharedPayload payload) {
        ServerRuntime runtime = ServerRuntime.get();
        try {
            SharedScriptStore.Info info = runtime.sharedScripts().find(payload.id());
            String source = runtime.sharedScripts().download(payload.id());
            send(player, new ScriptContentPayload(payload.id(), source, JsonFiles.GSON.toJson(info), ""));
        } catch (RuntimeException exception) {
            send(player, new ScriptContentPayload(payload.id(), "", "{}", exception.getMessage()));
        }
    }

    public static void upload(ServerPlayer player, UploadSharedPayload payload) {
        ServerRuntime runtime = ServerRuntime.get();
        try {
            String id = payload.id() == null || payload.id().isBlank() ? null : payload.id();
            SharedScriptStore.Info info = runtime.sharedScripts().upload(player.getUUID(),
                    player.getGameProfile().name(), id, payload.source(), runtime.settings());
            send(player, new ActionResultPayload(true, "upload",
                    JsonFiles.GSON.toJson(info)));
        } catch (RuntimeException exception) {
            send(player, new ActionResultPayload(false, "upload", exception.getMessage()));
        }
    }

    public static void delete(ServerPlayer player, DeleteSharedPayload payload) {
        ServerRuntime runtime = ServerRuntime.get();
        try {
            boolean admin = ServerRuntime.isAdmin(player);
            boolean deleted = runtime.sharedScripts().delete(payload.id(), player.getUUID(), admin);
            send(player, new ActionResultPayload(deleted, "delete",
                    deleted ? "Shared script deleted" : "Shared script not found"));
        } catch (RuntimeException exception) {
            send(player, new ActionResultPayload(false, "delete", exception.getMessage()));
        }
    }

    public static void run(ServerPlayer player, RunScriptPayload payload) {
        try {
            ServerJob job = ServerRuntime.get().jobs()
                    .start(player, payload.jobId(), payload.source(), payload.argumentsJson());
            send(player, ServerRuntime.get().jobs().snapshot(player, job.id));
        } catch (RuntimeException exception) {
            send(player, new ErrorPayload("run", exception.getMessage(), 0, 0));
        }
    }

    public static void cancel(ServerPlayer player, CancelJobPayload payload) {
        try {
            ServerRuntime.get().jobs().cancel(player, payload.jobId());
            send(player, new ActionResultPayload(true, "cancel", "Cancellation requested"));
        } catch (RuntimeException exception) {
            send(player, new ActionResultPayload(false, "cancel", exception.getMessage()));
        }
    }

    public static void getJob(ServerPlayer player, GetJobPayload payload) {
        try {
            send(player, ServerRuntime.get().jobs().snapshot(player, payload.jobId()));
        } catch (RuntimeException exception) {
            send(player, new ErrorPayload("job", exception.getMessage(), 0, 0));
        }
    }

    public static void getServerSettings(ServerPlayer player) {
        send(player, new ServerSettingsPayload(JsonFiles.GSON.toJson(ServerRuntime.get().settings())));
    }

    public static void updateServerSettings(ServerPlayer player, UpdateServerSettingsPayload payload) {
        if (!ServerRuntime.isAdmin(player)) {
            send(player, new ActionResultPayload(false, "settings", "Administrator permission required"));
            return;
        }
        try {
            ServerSettings settings = JsonFiles.GSON.fromJson(payload.settingsJson(), ServerSettings.class);
            if (settings == null) {
                throw new IllegalArgumentException("Settings are empty");
            }
            ServerRuntime.get().updateSettings(settings);
            send(player, new ActionResultPayload(true, "settings", "Server settings saved"));
            send(player, new ServerSettingsPayload(JsonFiles.GSON.toJson(settings)));
        } catch (RuntimeException exception) {
            send(player, new ActionResultPayload(false, "settings", exception.getMessage()));
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
