package dev.scriptautoce.client.ui;

import dev.scriptautoce.config.ClientSettings;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.BootstrapFiles;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientUiState {
    private static ClientSettings settings = ClientSettings.defaults();
    private static final LocalScriptRepository LOCAL_REPOSITORY = new LocalScriptRepository();
    private static final List<LocalScriptRepository.Summary> LOCAL_SCRIPTS = new ArrayList<>();
    private static final List<SharedScriptStore.Info> SHARED_SCRIPTS = new ArrayList<>();
    private static final Map<String, JobEventPayload> JOBS = new LinkedHashMap<>();
    private static boolean serverAvailable;
    private static boolean sharingEnabled;
    private static int serverMaxSourceBytes = 64 * 1024;
    private static String serverSettingsJson = "{}";

    private ClientUiState() {
    }

    public static void initialize() {
        BootstrapFiles.ensure();
        settings = ClientSettings.load();
        refreshLocal();
    }

    public static void refreshLocal() {
        LOCAL_SCRIPTS.clear();
        LOCAL_SCRIPTS.addAll(LOCAL_REPOSITORY.list());
    }

    public static ClientSettings settings() {
        return settings;
    }

    public static void updateSettings(ClientSettings updated) {
        settings = updated;
        settings.save();
    }

    public static LocalScriptRepository localRepository() {
        return LOCAL_REPOSITORY;
    }

    public static List<LocalScriptRepository.Summary> localScripts() {
        return List.copyOf(LOCAL_SCRIPTS);
    }

    public static List<SharedScriptStore.Info> sharedScripts() {
        return List.copyOf(SHARED_SCRIPTS);
    }

    public static void setSharedScripts(List<SharedScriptStore.Info> scripts) {
        SHARED_SCRIPTS.clear();
        SHARED_SCRIPTS.addAll(scripts);
    }

    public static boolean serverAvailable() {
        return serverAvailable;
    }

    public static void setServerAvailable(boolean available) {
        serverAvailable = available;
        if (!available) {
            SHARED_SCRIPTS.clear();
        }
    }

    public static boolean sharingEnabled() {
        return sharingEnabled;
    }

    public static void setSharingEnabled(boolean enabled) {
        sharingEnabled = enabled;
    }

    public static int serverMaxSourceBytes() {
        return serverMaxSourceBytes;
    }

    public static void setServerLimits(int maxSourceBytes) {
        serverMaxSourceBytes = maxSourceBytes;
    }

    public static String serverSettingsJson() {
        return serverSettingsJson;
    }

    public static void setServerSettingsJson(String settingsJson) {
        serverSettingsJson = settingsJson == null ? "{}" : settingsJson;
    }

    public static Map<String, JobEventPayload> jobs() {
        return Map.copyOf(JOBS);
    }

    public static void updateJob(JobEventPayload event) {
        JOBS.put(event.jobId(), event);
        if (event.eventType().equals("finished") || event.eventType().equals("failed")
                || event.eventType().equals("cancelled")) {
            // Keep completed jobs visible until the next connection.
        }
    }

    public static void clearJobs() {
        JOBS.clear();
    }

    public static void message(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Script Auto CE] " + message));
        }
    }

    public static void resetConnection() {
        serverAvailable = false;
        sharingEnabled = false;
        serverSettingsJson = "{}";
        SHARED_SCRIPTS.clear();
        JOBS.clear();
    }
}
