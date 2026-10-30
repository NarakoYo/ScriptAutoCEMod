package dev.scriptautoce.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScriptCompilerTest {
    @Test
    void compilesMetadataArgumentsAndBounds() {
        String source = """
                meta name "wall"
                meta on_error "continue"
                arg width: int = 3 min 1 max 8
                for x in range(0, width) {
                    run "say ${x}"
                    wait 1t
                }
                """;

        CompilationResult result = ScriptCompiler.compile("wall", source);

        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertEquals("wall", result.script().title());
        assertEquals("continue", result.script().onError());
        assertEquals(1, result.script().arguments().size());
        assertTrue(result.script().instructions().size() >= 8);
    }

    @Test
    void reportsDiagnosticWithLocation() {
        CompilationResult result = ScriptCompiler.compile("broken", """
                meta name "broken"
                let value =
                """);

        assertFalse(result.success());
        assertTrue(result.diagnostics().getFirst().line() >= 2);
        assertTrue(result.diagnostics().getFirst().message().contains("expression"));
    }

    @Test
    void rejectsUnknownFunctions() {
        CompilationResult result = ScriptCompiler.compile("unsafe", """
                let value = exploit("op @a")
                """);

        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("Unknown")));
    }

    @Test
    void rejectsEmptyScriptsAndBadFunctionArity() {
        CompilationResult empty = ScriptCompiler.compile("empty", "\n# only a comment\n");
        CompilationResult badArity = ScriptCompiler.compile("arity", """
                log "${replace("a", "b")}"
                """);

        assertFalse(empty.success());
        assertTrue(empty.diagnostics().getFirst().message().contains("no executable statements"));
        assertFalse(badArity.success());
        assertTrue(badArity.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("replace() expects 3 arguments")));
    }

    @Test
    void rejectsContradictoryStatementsBeforeExecution() {
        CompilationResult duplicate = ScriptCompiler.compile("duplicate", """
                let value = 1
                let value = 2
                log "${value}"
                """);
        CompilationResult condition = ScriptCompiler.compile("condition", """
                let value = 1
                if value {
                    log "bad"
                }
                """);
        CompilationResult unknown = ScriptCompiler.compile("unknown", """
                log "${missing}"
                """);

        assertFalse(duplicate.success());
        assertTrue(duplicate.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("already defined")));
        assertFalse(condition.success());
        assertTrue(condition.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("boolean")));
        assertFalse(unknown.success());
        assertTrue(unknown.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("Unknown variable")));
    }

    @Test
    void rejectsOversizedSources() {
        CompilationResult result = ScriptCompiler.compile("huge", "x".repeat(ScriptCompiler.MAX_SOURCE_BYTES + 1));

        assertFalse(result.success());
        assertTrue(result.diagnostics().getFirst().message().contains("64 KiB"));
    }

    @Test
    void hashesSourceStably() {
        String first = ScriptCompiler.sha256("meta name \"a\"");
        String second = ScriptCompiler.sha256("meta name \"a\"");
        String different = ScriptCompiler.sha256("meta name \"b\"");

        assertNotEquals(first, different);
        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void bundledFakePlayerExampleCompiles() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("assets/scriptautoce/examples/spawn_10_fake_players.sace")) {
            assertTrue(input != null);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            CompilationResult result = ScriptCompiler.compile("spawn_10_fake_players", source);

            assertTrue(result.success(), () -> result.diagnostics().toString());
            assertEquals("spawn_10_fake_players", result.script().title());
        }
    }
}
