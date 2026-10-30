package dev.scriptautoce.script;

public record ScriptDiagnostic(Severity severity, int line, int column, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public static ScriptDiagnostic error(int line, int column, String message) {
        return new ScriptDiagnostic(Severity.ERROR, line, column, message);
    }

    public static ScriptDiagnostic warning(int line, int column, String message) {
        return new ScriptDiagnostic(Severity.WARNING, line, column, message);
    }

    @Override
    public String toString() {
        return severity.name().toLowerCase() + ":" + line + ":" + column + " " + message;
    }
}
