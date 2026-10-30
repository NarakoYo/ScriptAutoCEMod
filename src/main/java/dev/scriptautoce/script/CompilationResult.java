package dev.scriptautoce.script;

import java.util.List;

public record CompilationResult(CompiledScript script, List<ScriptDiagnostic> diagnostics) {
    public CompilationResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean success() {
        return script != null && diagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.severity() == ScriptDiagnostic.Severity.ERROR);
    }
}
