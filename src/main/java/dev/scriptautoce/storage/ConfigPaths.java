package dev.scriptautoce.storage;

import dev.scriptautoce.ScriptAutoCe;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public final class ConfigPaths {
    private ConfigPaths() {
    }

    public static Path configRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve(ScriptAutoCe.MOD_ID);
    }

    public static Path client() {
        return configRoot().resolve("client");
    }

    public static Path server() {
        return configRoot().resolve("server");
    }

    public static Path gameRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("ScriptAutoCE");
    }

    public static Path clientSettings() {
        return client().resolve("settings.json");
    }

    public static Path localScripts() {
        return gameRoot().resolve("Scripts");
    }

    public static Path downloadCache() {
        return client().resolve("cache");
    }

    public static Path serverSettings() {
        return server().resolve("settings.json");
    }

    public static Path sharedIndex() {
        return gameRoot().resolve("Shared").resolve("index.json");
    }

    public static Path sharedScripts() {
        return gameRoot().resolve("Shared").resolve("Scripts");
    }

    public static Path auditLogs() {
        return gameRoot().resolve("Logs");
    }
}
