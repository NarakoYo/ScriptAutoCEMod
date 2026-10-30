package dev.scriptautoce.client.script;

import dev.scriptautoce.client.ui.ClientUiState;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import dev.scriptautoce.policy.CommandPolicy;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.CompiledScript;
import dev.scriptautoce.script.Interpreter;
import dev.scriptautoce.script.ScriptCompiler;
import dev.scriptautoce.script.ScriptException;
import dev.scriptautoce.storage.JsonFiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class DirectScriptJobManager {
    private static final int COMMANDS_PER_TICK = 2;
    private final Minecraft client;
    private final Map<String, Job> jobs = new HashMap<>();
    private final CommandPolicy policy = CommandPolicy.defaults();
    private long tickCounter;

    public DirectScriptJobManager(Minecraft client) {
        this.client = client;
    }

    public String start(String source, Map<String, String> arguments) {
        if (activeCount() > 0) {
            throw new IllegalArgumentException("A direct script is already running");
        }
        CompilationResult compilation = ScriptCompiler.compile("direct", source);
        if (!compilation.success()) {
            throw new IllegalArgumentException(compilation.diagnostics().getFirst().toString());
        }
        CompiledScript script = compilation.script();
        Interpreter interpreter = new Interpreter(script, arguments, new ClientScriptHost(client));
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, script, interpreter, tickCounter);
        jobs.put(id, job);
        publish(job, "started", "Direct command mode started");
        return id;
    }

    public void tick() {
        tickCounter++;
        if (client.player == null) {
            jobs.values().stream().filter(Job::active)
                    .forEach(job -> fail(job, "Client disconnected"));
            return;
        }
        for (Job job : new ArrayList<>(jobs.values())) {
            if (!job.active() || job.waitTicks > 0) {
                if (job.waitTicks > 0) {
                    job.waitTicks--;
                }
                continue;
            }
            if (tickCounter - job.startedTick > 6_000) {
                fail(job, "Runtime tick limit exceeded");
                continue;
            }
            if (job.count >= 10_000) {
                fail(job, "Command limit exceeded");
                continue;
            }
            int commands = 0;
            try {
                while (commands < COMMANDS_PER_TICK) {
                    Interpreter.StepResult result = job.interpreter.advance();
                    job.addLogs(job.interpreter.drainLogs());
                    if (result instanceof Interpreter.Command command) {
                        CommandPolicy.PolicyDecision decision = policy.evaluate(command.command());
                        if (!decision.allowed()) {
                            if (!decision.hardBlocked() && job.script.onDenied().equals("skip")) {
                                job.addLogs(List.of("Skipped blocked command: " + decision.reason()));
                            } else {
                                fail(job, decision.reason());
                                break;
                            }
                        } else {
                            client.player.connection.sendCommand(command.command());
                        }
                        commands++;
                        job.count++;
                        job.command = command.command();
                        job.progress = Math.min(99, job.count);
                    } else if (result instanceof Interpreter.Waiting waiting) {
                        job.waitTicks = waiting.ticks();
                        break;
                    } else if (result instanceof Interpreter.Finished finished) {
                        job.active = false;
                        job.progress = 100;
                        job.command = "";
                        job.addLogs(List.of(finished.message()));
                        publish(job, "finished", finished.message());
                        break;
                    }
                }
                if (job.active) {
                    publish(job, "progress", "Direct mode running");
                }
            } catch (ScriptException exception) {
                fail(job, exception.line() + ":" + exception.column() + " " + exception.getMessage());
            } catch (RuntimeException exception) {
                fail(job, exception.getMessage());
            }
        }
    }

    public boolean cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.active) {
            return false;
        }
        job.active = false;
        publish(job, "cancelled", "Cancelled");
        return true;
    }

    public void cancelAll() {
        jobs.values().stream().filter(Job::active).forEach(job -> {
            job.active = false;
            publish(job, "cancelled", "Client disconnected");
        });
    }

    private void fail(Job job, String message) {
        if (!job.active) {
            return;
        }
        job.active = false;
        job.addLogs(List.of("ERROR: " + message));
        publish(job, "failed", message);
    }

    private void publish(Job job, String type, String message) {
        ClientUiState.updateJob(new JobEventPayload(job.id, type, message, job.progress,
                job.command, JsonFiles.GSON.toJson(job.logs())));
    }

    private long activeCount() {
        return jobs.values().stream().filter(Job::active).count();
    }

    private static final class Job {
        private final String id;
        private final CompiledScript script;
        private final Interpreter interpreter;
        private final long startedTick;
        private final List<String> logs = new ArrayList<>();
        private boolean active = true;
        private int waitTicks;
        private int count;
        private int progress;
        private String command = "";

        private Job(String id, CompiledScript script, Interpreter interpreter, long startedTick) {
            this.id = id;
            this.script = script;
            this.interpreter = interpreter;
            this.startedTick = startedTick;
        }

        private boolean active() {
            return active;
        }

        private void addLogs(List<String> entries) {
            logs.addAll(entries);
            while (logs.size() > 100) {
                logs.removeFirst();
            }
        }

        private List<String> logs() {
            return List.copyOf(logs);
        }
    }
}
