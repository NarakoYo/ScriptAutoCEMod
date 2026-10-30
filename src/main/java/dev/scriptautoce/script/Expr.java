package dev.scriptautoce.script;

import java.util.List;

public sealed interface Expr permits Expr.Literal, Expr.Variable, Expr.Unary, Expr.Binary, Expr.Call,
        Expr.TemplateExpr {
    int line();

    record Literal(Value value, int line, int column) implements Expr {
    }

    record Variable(String name, int line, int column) implements Expr {
    }

    record Unary(String operator, Expr operand, int line, int column) implements Expr {
    }

    record Binary(Expr left, String operator, Expr right, int line, int column) implements Expr {
    }

    record Call(String name, List<Expr> arguments, int line, int column) implements Expr {
        public Call {
            arguments = List.copyOf(arguments);
        }
    }

    record TemplateExpr(Template template, int line, int column) implements Expr {
    }
}
