package dev.scriptautoce.server;

import com.google.gson.reflect.TypeToken;
import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.policy.CommandPolicy;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.CompiledScript;
import dev.scriptautoce.script.Interpreter;
import dev.scriptautoce.script.ScriptCompiler;
import dev.scriptautoce.script.ScriptDiagnostic;
import dev.scriptautoce.script.ScriptException;
import dev.scriptautoce.storage.AuditLog;
import dev.scriptautoce.storage.JsonFiles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.ParseResults;

final class ServerJobManager {
    private static final Type ARGUMENT_MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private final MinecraftServer server;
    private final Map<String, ServerJob> jobs = new HashMap<>();
    private ServerSettings settings;
    private CommandPolicy policy;
    private int emergencyTicks;

    ServerJobManager(MinecraftServer server, ServerSettings settings) {
        this.server = server;
        this.settings = settings;
        this.policy = settings.policy();
    }

    void updateSettings(ServerSettings settings) {
        this.settings = settings;
        this.policy = settings.policy();
    }

    ServerJob start(ServerPlayer player, String requestedId, String source, String argumentsJson) {
        if (!settings.sharingEnabled()) {
            // Sharing controls the shared library, not local execution.
        }
        if (activeCount(player.getUUID()) >= settings.maxConcurrentPerPlayer()) {
            throw new IllegalArgumentException("You already have the maximum number of active scripts");
        }
        if (activeCount() >= settings.maxConcurrentGlobal()) {
            throw new IllegalArgumentException("The server active script limit has been reached");
        }
        CompilationResult compilation = ScriptCompiler.compile("remote", source);
        if (!compilation.success()) {
            ScriptDiagnostic diagnostic = compilation.diagnostics().stream()
                    .filter(value -> value.severity() == ScriptDiagnostic.Severity.ERROR)
                    .findFirst().orElse(ScriptDiagnostic.error(1, 1, "Compilation failed"));
            throw new IllegalArgumentException(diagnostic.line() + ":" + diagnostic.column() + " "
                    + diagnostic.message());
        }
        CompiledScript script = compilation.script();
        if (script.instructions().size() > settings.maxInstructions()) {
            throw new IllegalArgumentException("Script exceeds the server instruction limit");
        }
        List<String> staticViolations =
                dev.scriptautoce.policy.PolicyPreflight.findStaticViolations(script, policy);
        if (!staticViolations.isEmpty()) {
            throw new IllegalArgumentException(staticViolations.getFirst());
        }
        Map<String, String> arguments = JsonFiles.GSON.fromJson(
                argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, ARGUMENT_MAP_TYPE);
        if (arguments == null) {
            arguments = Map.of();
        }
        String id = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString() : requestedId;
        if (jobs.containsKey(id)) {
            throw new IllegalArgumentException("A job with this id already exists");
        }
        int maxCommands = metadataLimit(script, "max_commands", settings.maxCommandsPerRun());
        int maxTicks = metadataLimit(script, "max_ticks", settings.maxRuntimeTicks());
        Interpreter interpreter = new Interpreter(script, arguments, new ServerScriptHost(player));
        ServerJob job = new ServerJob(id, player.getUUID(), script, interpreter,
                server.getTickCount(), maxCommands, maxTicks);
        jobs.put(id, job);
        AuditLog.write("job-start", Map.of(
                "job", job.id,
                "player", player.getUUID(),
                "name", script.title(),
                "hash", script.sourceHash()));
        sendEvent(player, job, "started", "Script started");
        return job;
    }

    void tick() {
        if (!CarpetConfigRules.emergencyStop) {
            emergencyTicks = 0;
        } else if (server.getCurrentSmoothedTickTime() > settings.emergencyTickMillis()) {
            emergencyTicks++;
            if (emergencyTicks >= 3) {
                stopAll("Emergency stop: server tick time exceeded " + settings.emergencyTickMillis() + " ms");
                emergencyTicks = 0;
            }
        } else {
            emergencyTicks = 0;
        }

        for (ServerJob job : new ArrayList<>(jobs.values())) {
            if (!job.active()) {
                if (job.completedTick >= 0 && server.getTickCount() - job.completedTick > 200) {
                    jobs.remove(job.id);
                }
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
            if (player == null) {
                job.finish(ServerJob.Status.CANCELLED, "Player disconnected", server.getTickCount());
                continue;
            }
            if (server.getTickCount() - job.startedTick > job.maxTicks) {
                fail(player, job, "Runtime tick limit exceeded");
                continue;
            }
            if (job.waitTicks > 0) {
                job.waitTicks--;
                job.status = ServerJob.Status.WAITING;
                continue;
            }
            job.status = ServerJob.Status.RUNNING;
            boolean yield = false;
            int commandsThisTick = 0;
            try {
                while (commandsThisTick < settings.maxCommandsPerTick() && !yield) {
                    Interpreter.StepResult result = job.interpreter.advance();
                    job.addLogs(job.interpreter.drainLogs());
                    if (result instanceof Interpreter.Command command) {
                        job.currentCommand = command.command();
                        CommandOutcome outcome = executeAsPlayer(player, command.command());
                        commandsThisTick++;
                        job.commandCount++;
                        job.progress = Math.min(99, job.commandCount * 100 / Math.max(1, job.maxCommands));
                        job.progressDirty = true;
                        AuditLog.write("command", Map.of(
                                "job", job.id,
                                "player", player.getUUID(),
                                "command", command.command(),
                                "success", outcome.success,
                                "denied", outcome.denied));
                        if (!outcome.success) {
                            String onDenied = job.script.onDenied();
                            String onError = job.script.onError();
                            if (outcome.hardBlocked) {
                                fail(player, job, outcome.message);
                                yield = true;
                            } else if (outcome.denied && onDenied.equals("skip")
                                    || !outcome.denied && onError.equals("continue")) {
                                job.addLogs(List.of("Skipped failed command: " + outcome.message));
                            } else {
                                fail(player, job, outcome.message);
                                yield = true;
                            }
                        }
                        if (job.commandCount >= job.maxCommands) {
                            fail(player, job, "Command limit exceeded");
                            yield = true;
                        }
                    } else if (result instanceof Interpreter.Waiting waiting) {
                        job.waitTicks = waiting.ticks();
                        job.status = ServerJob.Status.WAITING;
                        yield = true;
                    } else if (result instanceof Interpreter.Finished finished) {
                        job.progress = 100;
                        job.currentCommand = "";
                        job.addLogs(List.of(finished.message()));
                        job.finish(ServerJob.Status.COMPLETED, finished.message(), server.getTickCount());
                        sendEvent(player, job, "finished", finished.message());
                        AuditLog.write("job-finish", Map.of("job", job.id, "status", "completed"));
                        yield = true;
                    }
                }
                if (job.progressDirty && server.getTickCount() % 5 == 0 && job.active()) {
                    sendEvent(player, job, "progress", "Running");
                    job.progressDirty = false;
                }
            } catch (ScriptException exception) {
                fail(player, job, exception.line() + ":" + exception.column() + " " + exception.getMessage());
            } catch (RuntimeException exception) {
                fail(player, job, "Execution failure: " + exception.getMessage());
            }
        }
    }

    void cancel(ServerPlayer player, String jobId) {
        ServerJob job = jobs.get(jobId);
        if (job == null || !job.active()) {
            throw new IllegalArgumentException("Active job not found");
        }
        if (!job.playerId.equals(player.getUUID())) {
            throw new IllegalArgumentException("This job belongs to another player");
        }
        job.finish(ServerJob.Status.CANCELLED, "Cancelled by player", server.getTickCount());
        sendEvent(player, job, "cancelled", job.message);
        AuditLog.write("job-cancel", Map.of("job", job.id, "player", player.getUUID()));
    }

    void cancelAll(UUID playerId) {
        jobs.values().stream().filter(ServerJob::active)
                .filter(job -> job.playerId.equals(playerId))
                .forEach(job -> job.finish(ServerJob.Status.CANCELLED, "Player disconnected", server.getTickCount()));
    }

    void stopAll(String reason) {
        for (ServerJob job : jobs.values()) {
            if (job.active()) {
                job.finish(ServerJob.Status.CANCELLED, reason, server.getTickCount());
                ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
                if (player != null) {
                    sendEvent(player, job, "cancelled", reason);
                }
            }
        }
        AuditLog.write("emergency-stop", Map.of("reason", reason));
    }

    JobEventPayload snapshot(ServerPlayer player, String jobId) {
        ServerJob job = jobs.get(jobId);
        if (job == null || !job.playerId.equals(player.getUUID())) {
            throw new IllegalArgumentException("Job not found");
        }
        return payload(job, "snapshot", job.message);
    }

    List<JobEventPayload> snapshots(ServerPlayer player) {
        return jobs.values().stream()
                .filter(job -> job.playerId.equals(player.getUUID()))
                .sorted(Comparator.comparingLong(job -> job.startedTick))
                .map(job -> payload(job, "snapshot", job.message))
                .toList();
    }

    private CommandOutcome executeAsPlayer(ServerPlayer player, String command) {
        CommandPolicy.PolicyDecision decision = policy.evaluate(command);
        if (!decision.allowed()) {
            return new CommandOutcome(false, true, decision.hardBlocked(), decision.reason());
        }
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicInteger result = new AtomicInteger(0);
        CommandResultCallback callback = (success, value) -> {
            if (!success) {
                failed.set(true);
            } else {
                result.set(value);
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withCallback(callback);
        try {
            ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
            if (!parsed.getExceptions().isEmpty() || parsed.getContext().getCommand() == null
                    || parsed.getReader().canRead()) {
                return new CommandOutcome(false, true, false, "Command was rejected by the server");
            }
            server.getCommands().performCommand(parsed, command);
            if (failed.get()) {
                return new CommandOutcome(false, false, false, "Command execution failed");
            }
            return new CommandOutcome(true, false, false, "ok:" + result.get());
        } catch (Exception exception) {
            return new CommandOutcome(false, false, false, "Command error: " + exception.getMessage());
        }
    }

    private void fail(ServerPlayer player, ServerJob job, String message) {
        if (!job.active()) {
            return;
        }
        job.addLogs(List.of("ERROR: " + message));
        job.finish(ServerJob.Status.FAILED, message, server.getTickCount());
        sendEvent(player, job, "failed", message);
        AuditLog.write("job-fail", Map.of("job", job.id, "reason", message));
    }

    private void sendEvent(ServerPlayer player, ServerJob job, String type, String message) {
        if (ServerPlayNetworking.canSend(player, JobEventPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload(job, type, message));
        }
    }

    private JobEventPayload payload(ServerJob job, String type, String message) {
        return new JobEventPayload(job.id, type, message, job.progress,
                trim(job.currentCommand, 1_024), JsonFiles.GSON.toJson(job.logs()));
    }

    private long activeCount() {
        return jobs.values().stream().filter(ServerJob::active).count();
    }

    private long activeCount(UUID playerId) {
        return jobs.values().stream().filter(ServerJob::active)
                .filter(job -> job.playerId.equals(playerId)).count();
    }

    private static int metadataLimit(CompiledScript script, String key, int fallback) {
        String value = script.metadata().get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.min(fallback, Math.max(1, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private record CommandOutcome(boolean success, boolean denied, boolean hardBlocked, String message) {
    }
}
