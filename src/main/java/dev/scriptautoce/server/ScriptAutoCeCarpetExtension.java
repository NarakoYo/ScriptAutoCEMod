package dev.scriptautoce.server;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import com.mojang.brigadier.CommandDispatcher;
import dev.scriptautoce.ScriptAutoCe;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

public final class ScriptAutoCeCarpetExtension implements CarpetExtension {
    private final SettingsManager settingsManager =
            new SettingsManager(ScriptAutoCe.VERSION, "scriptautoce-config", "Script Auto CE");

    @Override
    public void onGameStarted() {
        settingsManager.parseSettingsClass(CarpetConfigRules.class);
        settingsManager.registerRuleObserver((source, changedRule, userInput) -> {
            if (ServerRuntimeReady.isReady()) {
                ServerRuntime.get().applyCarpetSettings();
            }
        });
    }

    @Override
    public void onServerLoaded(MinecraftServer server) {
        ServerRuntime.initialize(server);
    }

    @Override
    public void onTick(MinecraftServer server) {
        if (ServerRuntimeReady.isReady()) {
            ServerRuntime.get().tick();
        }
    }

    @Override
    public void onPlayerLoggedOut(ServerPlayer player) {
        if (ServerRuntimeReady.isReady()) {
            ServerRuntime.get().playerLoggedOut(player);
        }
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        ServerRuntime.shutdown();
    }

    @Override
    public void onReload(MinecraftServer server) {
        if (ServerRuntimeReady.isReady()) {
            ServerRuntime.get().reloadSettings();
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext commandBuildContext) {
        ScriptAutoCeCommand.register(dispatcher);
    }

    @Override
    public SettingsManager extensionSettingsManager() {
        return settingsManager;
    }

    @Override
    public String version() {
        return ScriptAutoCe.VERSION;
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return Map.of(
                "scriptautoce-config.rule.sharingEnabled.desc",
                "Enable shared script upload and download",
                "scriptautoce-config.rule.maxCommandsPerTick.desc",
                "Maximum commands executed per server tick",
                "scriptautoce-config.rule.maxRuntimeTicks.desc",
                "Maximum runtime of one script in ticks",
                "scriptautoce-config.rule.emergencyStop.desc",
                "Stop scripts when server tick time exceeds the emergency threshold"
        );
    }

    private static final class ServerRuntimeReady {
        private static boolean isReady() {
            try {
                ServerRuntime.get();
                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        }
    }
}
