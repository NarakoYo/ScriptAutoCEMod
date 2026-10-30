package dev.scriptautoce.storage;

import dev.scriptautoce.ScriptAutoCe;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public final class ConfigPaths {
    private ConfigPaths() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve(ScriptAutoCe.MOD_ID);
    }

    public static Path client() {
        return root().resolve("client");
    }

    public static Path server() {
        return root().resolve("server");
    }

    public static Path clientSettings() {
        return client().resolve("settings.json");
    }

    public static Path localScripts() {
        return client().resolve("scripts");
    }

    public static Path downloadCache() {
        return client().resolve("cache");
    }

    public static Path serverSettings() {
        return server().resolve("settings.json");
    }

    public static Path sharedIndex() {
        return server().resolve("shared-index.json");
    }

    public static Path sharedScripts() {
        return server().resolve("shared");
    }

    public static Path auditLogs() {
        return server().resolve("logs");
    }
}
