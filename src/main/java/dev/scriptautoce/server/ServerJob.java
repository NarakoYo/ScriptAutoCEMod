package dev.scriptautoce.server;

import dev.scriptautoce.script.CompiledScript;
import dev.scriptautoce.script.Interpreter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

final class ServerJob {
    enum Status {
        RUNNING,
        WAITING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    final String id;
    final UUID playerId;
    final CompiledScript script;
    final Interpreter interpreter;
    final long startedTick;
    final int maxCommands;
    final int maxTicks;
    final Deque<String> logs = new ArrayDeque<>();
    Status status = Status.RUNNING;
    int waitTicks;
    int commandCount;
    int progress;
    String currentCommand = "";
    String message = "";
    long completedTick = -1;
    boolean progressDirty;

    ServerJob(String id, UUID playerId, CompiledScript script, Interpreter interpreter,
              long startedTick, int maxCommands, int maxTicks) {
        this.id = id;
        this.playerId = playerId;
        this.script = script;
        this.interpreter = interpreter;
        this.startedTick = startedTick;
        this.maxCommands = maxCommands;
        this.maxTicks = maxTicks;
    }

    void addLogs(List<String> entries) {
        for (String entry : entries) {
            logs.addLast(entry);
            while (logs.size() > 100) {
                logs.removeFirst();
            }
        }
        progressDirty = true;
    }

    List<String> logs() {
        return List.copyOf(logs);
    }

    void finish(Status finalStatus, String finalMessage, long tick) {
        status = finalStatus;
        message = finalMessage;
        completedTick = tick;
        progressDirty = true;
    }

    boolean active() {
        return status == Status.RUNNING || status == Status.WAITING;
    }
}
