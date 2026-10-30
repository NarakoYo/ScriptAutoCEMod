package dev.scriptautoce.server;

import carpet.api.settings.Rule;
import dev.scriptautoce.config.ServerSettings;

public final class CarpetConfigRules {
    @Rule(categories = {"scriptautoce"})
    public static boolean sharingEnabled = true;

    @Rule(categories = {"scriptautoce"}, strict = false)
    public static int maxCommandsPerTick = 4;

    @Rule(categories = {"scriptautoce"}, strict = false)
    public static int maxRuntimeTicks = 6_000;

    @Rule(categories = {"scriptautoce"})
    public static boolean emergencyStop = true;

    private CarpetConfigRules() {
    }

    static void apply(ServerSettings settings) {
        sharingEnabled = settings.sharingEnabled();
        maxCommandsPerTick = settings.maxCommandsPerTick();
        maxRuntimeTicks = settings.maxRuntimeTicks();
    }
}
