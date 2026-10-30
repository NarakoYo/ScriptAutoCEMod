package dev.scriptautoce.client.ui;

import dev.scriptautoce.config.ClientSettings;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.BootstrapFiles;
import dev.scriptautoce.storage.JsonFiles;
import dev.scriptautoce.storage.LocalScriptRepository;
import dev.scriptautoce.storage.SharedScriptStore;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ClientUiState {
    private static ClientSettings settings = ClientSettings.defaults();
    private static final LocalScriptRepository LOCAL_REPOSITORY = new LocalScriptRepository();
    private static final List<LocalScriptRepository.Summary> LOCAL_SCRIPTS = new ArrayList<>();
    private static final List<SharedScriptStore.Info> SHARED_SCRIPTS = new ArrayList<>();
    private static final Map<String, JobEventPayload> JOBS = new LinkedHashMap<>();
    private static final Map<String, List<String>> JOB_LOGS = new LinkedHashMap<>();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static boolean serverAvailable;
    private static boolean sharingEnabled;
    private static Runnable dataListener = () -> {
    };
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

    public static void removeSharedScript(String id) {
        SHARED_SCRIPTS.removeIf(script -> script.id().equals(id));
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

    public static JobEventPayload job(String id) {
        return JOBS.get(id);
    }

    public static List<JobEventPayload> activeJobs() {
        return JOBS.values().stream()
                .filter(job -> JobReasonDisplay.active(job.reasonCode()))
                .sorted(java.util.Comparator.comparingLong(JobEventPayload::startedAtMillis))
                .toList();
    }

    public static List<JobEventPayload> jobHistory() {
        return JOBS.values().stream()
                .filter(job -> !JobReasonDisplay.active(job.reasonCode()))
                .sorted(java.util.Comparator.comparingLong(JobEventPayload::endedAtMillis).reversed())
                .toList();
    }

    public static void updateJob(JobEventPayload event) {
        JOBS.put(event.jobId(), event);
    }

    public static void removeJob(String id) {
        JOBS.remove(id);
        JOB_LOGS.remove(id);
    }

    public static void setDataListener(Runnable listener) {
        dataListener = listener == null ? () -> {
        } : listener;
    }

    public static void clearDataListener() {
        dataListener = () -> {
        };
    }

    public static void notifyDataChanged() {
        dataListener.run();
    }

    public static List<String> consumeNewLogs(JobEventPayload event) {
        List<String> current;
        try {
            current = JsonFiles.GSON.fromJson(event.logsJson(), STRING_LIST_TYPE);
        } catch (RuntimeException exception) {
            current = List.of();
        }
        if (current == null) {
            current = List.of();
        }
        List<String> previous = JOB_LOGS.getOrDefault(event.jobId(), List.of());
        List<String> result = new ArrayList<>();
        if (current.size() > previous.size()
                && current.subList(0, previous.size()).equals(previous)) {
            result.addAll(current.subList(previous.size(), current.size()));
        } else if (!current.equals(previous)) {
            int common = 0;
            while (common < previous.size() && common < current.size()
                    && previous.get(common).equals(current.get(common))) {
                common++;
            }
            result.addAll(current.subList(Math.min(common, current.size()), current.size()));
        }
        JOB_LOGS.put(event.jobId(), List.copyOf(current));
        return List.copyOf(result);
    }

    public static void clearJobs() {
        JOBS.clear();
        JOB_LOGS.clear();
    }

    public enum MessageTone {
        INFO(ChatFormatting.AQUA, ChatFormatting.WHITE),
        SUCCESS(ChatFormatting.GREEN, ChatFormatting.GREEN),
        WARNING(ChatFormatting.YELLOW, ChatFormatting.YELLOW),
        ERROR(ChatFormatting.RED, ChatFormatting.RED),
        MUTED(ChatFormatting.DARK_AQUA, ChatFormatting.GRAY);

        private final ChatFormatting prefix;
        private final ChatFormatting text;

        MessageTone(ChatFormatting prefix, ChatFormatting text) {
            this.prefix = prefix;
            this.text = text;
        }
    }

    public static void message(String message) {
        message(MessageTone.INFO, message);
    }

    public static void message(MessageTone tone, String message) {
        message(tone, Component.literal(message));
    }

    public static void message(MessageTone tone, Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            MutableComponent component = Component.literal("[Script Auto CE] ").withStyle(tone.prefix);
            component.append(message.copy().withStyle(tone.text));
            client.player.sendSystemMessage(component);
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
