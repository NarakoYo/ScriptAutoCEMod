package dev.scriptautoce.script;

import java.util.List;
import java.util.Map;

public record CompiledScript(
        String sourceName,
        String sourceHash,
        Map<String, String> metadata,
        List<ScriptArgument> arguments,
        List<Instruction> instructions
) {
    public CompiledScript {
        metadata = Map.copyOf(metadata);
        arguments = List.copyOf(arguments);
        instructions = List.copyOf(instructions);
    }

    public String title() {
        return metadata.getOrDefault("name", sourceName);
    }

    public String onError() {
        return metadata.getOrDefault("on_error", "stop");
    }

    public String onDenied() {
        return metadata.getOrDefault("on_denied", "stop");
    }

    public record ScriptArgument(
            String name,
            String type,
            Expr defaultValue,
            Expr min,
            Expr max,
            int line
    ) {
    }

    public sealed interface Instruction permits Push, Declare, Store, Pop, Jump, JumpIfFalse,
            RunCommand, Log, Wait, Fail, Return {
        int line();
    }

    public record Push(Expr expression, int line) implements Instruction {
    }

    public record Declare(String name, int line) implements Instruction {
    }

    public record Store(String name, int line) implements Instruction {
    }

    public record Pop(int line) implements Instruction {
    }

    public record Jump(int target, int line) implements Instruction {
    }

    public record JumpIfFalse(int target, int line) implements Instruction {
    }

    public record RunCommand(Template command, int line) implements Instruction {
    }

    public record Log(Template message, int line) implements Instruction {
    }

    public record Wait(int ticks, int line) implements Instruction {
    }

    public record Fail(Template message, int line) implements Instruction {
    }

    public record Return(int line) implements Instruction {
    }
}
