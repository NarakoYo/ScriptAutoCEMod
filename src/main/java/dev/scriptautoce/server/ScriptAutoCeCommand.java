package dev.scriptautoce.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.storage.JsonFiles;
import dev.scriptautoce.storage.SharedScriptStore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class ScriptAutoCeCommand {
    private ScriptAutoCeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scriptautoce")
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("stop")
                        .executes(context -> stopAllOwned(context.getSource()))
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) {
                                        return builder.buildFuture();
                                    }
                                    return SharedSuggestionProvider.suggest(
                                            ServerRuntime.get().jobs().snapshots(player).stream()
                                                    .map(JobEventPayload::jobId).toList(), builder);
                                })
                                .executes(context -> stop(context.getSource(),
                                        StringArgumentType.getString(context, "job")))))
                .then(Commands.literal("run")
                        .then(Commands.argument("script", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        ServerRuntime.get().sharedScripts().list("").stream()
                                                .map(SharedScriptStore.Info::id).toList(), builder))
                                .executes(context -> run(context.getSource(),
                                        StringArgumentType.getString(context, "script"), ""))
                                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                        .executes(context -> run(context.getSource(),
                                                StringArgumentType.getString(context, "script"),
                                                StringArgumentType.getString(context, "arguments"))))))
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("config")
                        .executes(context -> config(context.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        var entries = ServerRuntime.get().sharedScripts().list("");
        source.sendSuccess(() -> Component.translatable("command.scriptautoce.shared_count", entries.size()), false);
        for (SharedScriptStore.Info entry : entries.stream().limit(50).toList()) {
            source.sendSuccess(() -> Component.translatable("command.scriptautoce.shared_entry",
                    entry.title(), entry.id(), entry.ownerName(), entry.revision()), false);
        }
        return entries.size();
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        var jobs = ServerRuntime.get().jobs().snapshots(player);
        source.sendSuccess(() -> Component.translatable("command.scriptautoce.job_count", jobs.size()), false);
        for (JobEventPayload job : jobs) {
            source.sendSuccess(() -> Component.translatable("command.scriptautoce.job_entry",
                    job.jobId(), job.eventType(), job.progress(), job.message()), false);
        }
        return jobs.size();
    }

    private static int stopAllOwned(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        var jobs = ServerRuntime.get().jobs().snapshots(player);
        for (JobEventPayload job : jobs) {
            try {
                ServerRuntime.get().jobs().cancel(player, job.jobId());
            } catch (RuntimeException ignored) {
            }
        }
        source.sendSuccess(() -> Component.translatable("command.scriptautoce.stopped"), false);
        return jobs.size();
    }

    private static int stop(CommandSourceStack source, String id) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        try {
            ServerRuntime.get().jobs().cancel(player, id);
            source.sendSuccess(() -> Component.translatable("command.scriptautoce.cancel_requested"), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int run(CommandSourceStack source, String scriptName, String rawArguments) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        SharedScriptStore store = ServerRuntime.get().sharedScripts();
        SharedScriptStore.Info info = store.list("").stream()
                .filter(candidate -> candidate.id().equals(scriptName)
                        || candidate.title().equalsIgnoreCase(scriptName))
                .findFirst().orElse(null);
        if (info == null) {
            source.sendFailure(Component.translatable("command.scriptautoce.not_found", scriptName));
            return 0;
        }
        try {
            String script = store.download(info.id());
            String arguments = JsonFiles.GSON.toJson(parseArguments(rawArguments));
            ServerJob job = ServerRuntime.get().jobs().start(player, null, script, arguments);
            source.sendSuccess(() -> Component.translatable("command.scriptautoce.started", job.id), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandSourceStack source) {
        ServerRuntime.get().reloadSettings();
        source.sendSuccess(() -> Component.translatable("command.scriptautoce.reloaded"), true);
        return 1;
    }

    private static int config(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(JsonFiles.GSON.toJson(ServerRuntime.get().settings())), false);
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.scriptautoce.player_only"));
        }
        return player;
    }

    private static Map<String, String> parseArguments(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : raw.strip().split("\\s+")) {
            int separator = token.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Arguments must use key=value");
            }
            result.put(token.substring(0, separator), token.substring(separator + 1));
        }
        return result;
    }
}
