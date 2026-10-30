package dev.scriptautoce.config;

import dev.scriptautoce.policy.CommandPolicy;
import dev.scriptautoce.storage.ConfigPaths;
import dev.scriptautoce.storage.JsonFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ServerSettings(
        boolean sharingEnabled,
        int maxSourceBytes,
        int maxInstructions,
        int maxCommandsPerRun,
        int maxCommandsPerTick,
        int maxRuntimeTicks,
        int maxConcurrentPerPlayer,
        int maxConcurrentGlobal,
        int maxUploadsPerPlayer,
        long maxSharedBytesPerPlayer,
        int emergencyTickMillis,
        List<String> blockedCommands,
        List<String> allowedCommands
) {
    public ServerSettings {
        maxSourceBytes = clamp(maxSourceBytes, 1_024, 64 * 1024, 64 * 1024);
        maxInstructions = clamp(maxInstructions, 100, 2_000, 2_000);
        maxCommandsPerRun = clamp(maxCommandsPerRun, 1, 100_000, 10_000);
        maxCommandsPerTick = clamp(maxCommandsPerTick, 1, 64, 4);
        maxRuntimeTicks = clamp(maxRuntimeTicks, 20, 72_000, 6_000);
        maxConcurrentPerPlayer = clamp(maxConcurrentPerPlayer, 1, 8, 1);
        maxConcurrentGlobal = clamp(maxConcurrentGlobal, 1, 64, 8);
        maxUploadsPerPlayer = clamp(maxUploadsPerPlayer, 1, 256, 32);
        maxSharedBytesPerPlayer = Math.max(64 * 1024, maxSharedBytesPerPlayer);
        emergencyTickMillis = clamp(emergencyTickMillis, 50, 5_000, 250);
        blockedCommands = blockedCommands == null || blockedCommands.isEmpty()
                ? List.copyOf(new java.util.TreeSet<>(CommandPolicy.DEFAULT_BLOCKED))
                : List.copyOf(blockedCommands);
        allowedCommands = allowedCommands == null ? List.of() : List.copyOf(allowedCommands);
    }

    public static ServerSettings defaults() {
        return new ServerSettings(true, 64 * 1024, 2_000, 10_000, 4, 6_000, 1, 8, 32,
                1024L * 1024L, 250, new ArrayList<>(CommandPolicy.DEFAULT_BLOCKED), List.of());
    }

    public static ServerSettings load() {
        ServerSettings loaded = JsonFiles.read(ConfigPaths.serverSettings(), ServerSettings.class, defaults());
        return loaded == null ? defaults() : loaded;
    }

    public void save() {
        JsonFiles.write(ConfigPaths.serverSettings(), this);
    }

    public CommandPolicy policy() {
        return new CommandPolicy(new java.util.HashSet<>(blockedCommands), new java.util.HashSet<>(allowedCommands));
    }

    private static int clamp(int value, int minimum, int maximum, int fallback) {
        if (value <= 0) {
            value = fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
