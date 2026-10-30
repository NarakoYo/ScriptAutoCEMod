package dev.scriptautoce.script;

public final class ScriptException extends RuntimeException {
    private final int line;
    private final int column;

    public ScriptException(int line, int column, String message) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
