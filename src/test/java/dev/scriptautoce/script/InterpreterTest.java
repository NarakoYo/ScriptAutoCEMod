package dev.scriptautoce.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpreterTest {
    private static final ScriptHost HOST = new ScriptHost() {
        @Override
        public Position position() {
            return new Position(10, 64, -2);
        }

        @Override
        public String blockAt(String x, String y, String z) {
            return "minecraft:stone";
        }

        @Override
        public List<String> players() {
            return List.of("Alex");
        }

        @Override
        public List<String> entities(String type, double radius) {
            return List.of("entity-1");
        }

        @Override
        public long score(String objective, String target) {
            return 7;
        }
    };

    @Test
    void executesLoopCommandsAndWaits() {
        CompiledScript script = compile("""
                meta name "loop"
                arg width: int = 3 min 1 max 5
                for x in range(0, width) {
                    run "say ${x}"
                    wait 1t
                }
                log "done"
                """);
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);
        List<String> commands = new ArrayList<>();
        int waits = 0;

        for (int guard = 0; guard < 100 && !interpreter.finished(); guard++) {
            Interpreter.StepResult result = interpreter.advance();
            if (result instanceof Interpreter.Command command) {
                commands.add(command.command());
            } else if (result instanceof Interpreter.Waiting) {
                waits++;
            }
        }

        assertEquals(List.of("say 0", "say 1", "say 2"), commands);
        assertEquals(3, waits);
        assertEquals(List.of("done"), interpreter.drainLogs());
    }

    @Test
    void appliesSuppliedArgumentsAndFormatting() {
        CompiledScript script = compile("""
                meta name "args"
                arg count: int = 2 min 1 max 4
                let doubled = count * 2
                run "say ${doubled}"
                """);
        Interpreter interpreter = new Interpreter(script, Map.of("count", "3"), HOST);

        Interpreter.Command command = (Interpreter.Command) interpreter.advance();

        assertEquals("say 6", command.command());
    }

    @Test
    void rejectsPlainStringsInCommands() {
        CompiledScript script = compile("""
                meta name "string"
                arg text: string = "hello world"
                run "say ${text}"
                """);
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);

        assertThrows(ScriptException.class, interpreter::advance);
    }

    @Test
    void quotedStringsAreSafeCommandTokens() {
        CompiledScript script = compile("""
                meta name "quoted"
                arg text: string = "hello world"
                run "say ${quote(text)}"
                """);
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);

        Interpreter.Command command = (Interpreter.Command) interpreter.advance();

        assertEquals("say \"hello world\"", command.command());
    }

    @Test
    void exposesReadOnlyQueries() {
        CompiledScript script = compile("""
                meta name "query"
                let p = pos()
                log "${p}"
                log "${block("~", "~-1", "~")}"
                log "${score("points", "Alex")}"
                """);
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);

        interpreter.advance();

        assertEquals(List.of("10 64 -2", "minecraft:stone", "7"), interpreter.drainLogs());
    }

    @Test
    void rejectsZeroStepLoop() {
        CompiledScript script = compile("""
                meta name "loop"
                for x in range(0, 3, 0) {
                    log "never"
                }
                """);
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);

        assertThrows(ScriptException.class, interpreter::advance);
    }

    @Test
    void fakePlayerExampleSpawnsTenPlayersAtSamePosition() throws IOException {
        CompiledScript script;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("assets/scriptautoce/examples/spawn_10_fake_players.sace")) {
            assertTrue(input != null);
            script = compile(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Interpreter interpreter = new Interpreter(script, Map.of(), HOST);
        List<String> commands = new ArrayList<>();

        for (int guard = 0; guard < 100 && !interpreter.finished(); guard++) {
            Interpreter.StepResult result = interpreter.advance();
            if (result instanceof Interpreter.Command command) {
                commands.add(command.command());
            }
        }

        assertEquals(10, commands.size());
        for (int index = 0; index < commands.size(); index++) {
            assertEquals("player sace_bot_" + index + " spawn at 10 64 -2", commands.get(index));
        }
    }

    private static CompiledScript compile(String source) {
        CompilationResult result = ScriptCompiler.compile("test", source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        return result.script();
    }
}
