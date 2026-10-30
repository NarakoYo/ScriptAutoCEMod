package dev.scriptautoce.config;

import dev.scriptautoce.ExecutionMode;
import dev.scriptautoce.storage.ConfigPaths;
import dev.scriptautoce.storage.JsonFiles;
import java.nio.file.Path;

public record ClientSettings(
        ExecutionMode executionMode,
        boolean confirmBeforeRun,
        boolean showDirectModeWarning,
        int pageSize
) {
    public ClientSettings {
        if (executionMode == null) {
            executionMode = ExecutionMode.AUTO;
        }
        pageSize = Math.max(4, Math.min(12, pageSize));
    }

    public static ClientSettings defaults() {
        return new ClientSettings(ExecutionMode.AUTO, true, true, 7);
    }

    public static ClientSettings load() {
        Path path = ConfigPaths.clientSettings();
        ClientSettings loaded = JsonFiles.read(path, ClientSettings.class, defaults());
        return loaded == null ? defaults() : loaded;
    }

    public void save() {
        JsonFiles.write(ConfigPaths.clientSettings(), this);
    }

    public ClientSettings withMode(ExecutionMode mode) {
        return new ClientSettings(mode, confirmBeforeRun, showDirectModeWarning, pageSize);
    }

    public ClientSettings withConfirmation(boolean value) {
        return new ClientSettings(executionMode, value, showDirectModeWarning, pageSize);
    }
}
