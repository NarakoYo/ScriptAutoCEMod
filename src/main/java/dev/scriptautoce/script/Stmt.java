package dev.scriptautoce.script;

import java.util.List;

sealed interface Stmt permits Stmt.Let, Stmt.Set, Stmt.If, Stmt.For, Stmt.Wait, Stmt.Run, Stmt.Log,
        Stmt.Fail, Stmt.Return {
    int line();

    record Let(String name, Expr value, int line) implements Stmt {
    }

    record Set(String name, Expr value, int line) implements Stmt {
    }

    record If(Expr condition, List<Stmt> thenBranch, List<Stmt> elseBranch, int line) implements Stmt {
        public If {
            thenBranch = List.copyOf(thenBranch);
            elseBranch = List.copyOf(elseBranch);
        }
    }

    record For(String variable, Expr start, Expr end, Expr step, List<Stmt> body, int line) implements Stmt {
        public For {
            body = List.copyOf(body);
        }
    }

    record Wait(int ticks, int line) implements Stmt {
    }

    record Run(Template command, int line) implements Stmt {
    }

    record Log(Template message, int line) implements Stmt {
    }

    record Fail(Template message, int line) implements Stmt {
    }

    record Return(int line) implements Stmt {
    }
}
