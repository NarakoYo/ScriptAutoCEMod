package dev.scriptautoce.script;

import java.util.List;
import java.util.Map;

record ScriptAst(Map<String, MetaValue> metadata, List<Argument> arguments, List<Stmt> statements) {
    record MetaValue(Template template, int line) {
    }

    record Argument(String name, String type, Expr defaultValue, Expr min, Expr max, int line) {
    }
}
