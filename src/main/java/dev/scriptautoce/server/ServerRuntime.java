package dev.scriptautoce.server;

import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.storage.AuditLog;
import dev.scriptautoce.storage.BootstrapFiles;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class ServerRuntime {
    private static ServerRuntime instance;
    private final MinecraftServer server;
    private final SharedScriptStore sharedScripts = new SharedScriptStore();
    private ServerSettings settings;
    private ServerJobManager jobs;

    private ServerRuntime(MinecraftServer server) {
        this.server = server;
        BootstrapFiles.ensure();
        reloadSettings();
        this.jobs = new ServerJobManager(server, settings);
    }

    public static void initialize(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new ServerRuntime(server);
        }
    }

    public static ServerRuntime get() {
        if (instance == null) {
            throw new IllegalStateException("Script Auto CE server runtime is not ready");
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.jobs.stopAll("Server stopping");
            instance = null;
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerSettings settings() {
        return settings;
    }

    public SharedScriptStore sharedScripts() {
        return sharedScripts;
    }

    public ServerJobManager jobs() {
        return jobs;
    }

    public void tick() {
        jobs.tick();
    }

    public void playerLoggedOut(ServerPlayer player) {
        jobs.cancelAll(player.getUUID());
    }

    public void reloadSettings() {
        settings = ServerSettings.load();
        CarpetConfigRules.apply(settings);
        if (jobs != null) {
            jobs.updateSettings(settings);
        }
    }

    public void updateSettings(ServerSettings updated) {
        settings = updated;
        settings.save();
        CarpetConfigRules.apply(settings);
        jobs.updateSettings(settings);
        AuditLog.write("settings-update", Map.of("sharing", settings.sharingEnabled()));
    }

    public void applyCarpetSettings() {
        ServerSettings current = settings;
        ServerSettings updated = new ServerSettings(
                CarpetConfigRules.sharingEnabled,
                current.maxSourceBytes(),
                current.maxInstructions(),
                current.maxCommandsPerRun(),
                CarpetConfigRules.maxCommandsPerTick,
                CarpetConfigRules.maxRuntimeTicks,
                current.maxConcurrentPerPlayer(),
                current.maxConcurrentGlobal(),
                current.maxUploadsPerPlayer(),
                current.maxSharedBytesPerPlayer(),
                current.emergencyTickMillis(),
                current.blockedCommands(),
                current.allowedCommands()
        );
        updateSettings(updated);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }
}
