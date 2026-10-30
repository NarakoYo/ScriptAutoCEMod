package dev.scriptautoce.script;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ScriptCompiler {
    public static final int MAX_SOURCE_BYTES = 64 * 1024;
    public static final int MAX_INSTRUCTIONS = 2_000;
    private static final Set<String> ARGUMENT_TYPES =
            Set.of("int", "double", "bool", "string", "token", "vec3", "selector");
    private static final Set<String> BUILTINS =
            Set.of("pos", "block", "players", "entities", "score", "token", "quote", "json",
                    "min", "max", "abs", "round");

    private ScriptCompiler() {
    }

    public static CompilationResult compile(String sourceName, String source) {
        if (source == null) {
            return failure(sourceName, List.of(ScriptDiagnostic.error(1, 1, "Script is empty")));
        }
        int size = source.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_SOURCE_BYTES) {
            return failure(sourceName,
                    List.of(ScriptDiagnostic.error(1, 1, "Script exceeds 64 KiB")));
        }

        List<ScriptDiagnostic> diagnostics = new ArrayList<>();
        try {
            ScriptAst ast = Parser.parse(source);
            Map<String, String> metadata = compileMetadata(ast.metadata(), diagnostics);
            List<CompiledScript.ScriptArgument> arguments = compileArguments(ast.arguments(), diagnostics);
            List<CompiledScript.Instruction> instructions = new InstructionBuilder(diagnostics).compile(ast.statements());
            if (instructions.size() > MAX_INSTRUCTIONS) {
                diagnostics.add(ScriptDiagnostic.error(1, 1,
                        "Script exceeds " + MAX_INSTRUCTIONS + " instructions"));
            }
            validateExpressions(ast.statements(), diagnostics);
            CompiledScript script = new CompiledScript(sourceName, sha256(source), metadata,
                    arguments, instructions);
            return new CompilationResult(script, diagnostics);
        } catch (ScriptException exception) {
            diagnostics.add(ScriptDiagnostic.error(exception.line(), exception.column(), exception.getMessage()));
            return new CompilationResult(null, diagnostics);
        } catch (RuntimeException exception) {
            diagnostics.add(ScriptDiagnostic.error(1, 1, "Compiler failure: " + exception.getMessage()));
            return new CompilationResult(null, diagnostics);
        }
    }

    private static CompilationResult failure(String sourceName, List<ScriptDiagnostic> diagnostics) {
        return new CompilationResult(null, diagnostics);
    }

    private static Map<String, String> compileMetadata(Map<String, ScriptAst.MetaValue> values,
                                                       List<ScriptDiagnostic> diagnostics) {
        Map<String, String> metadata = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ScriptAst.MetaValue> entry : values.entrySet()) {
            String text = entry.getValue().template().staticText();
            if (text == null) {
                diagnostics.add(ScriptDiagnostic.error(entry.getValue().line(), 1,
                        "Metadata values cannot contain interpolation"));
                continue;
            }
            metadata.put(entry.getKey(), text);
        }
        metadata.putIfAbsent("name", "untitled");
        String onError = metadata.getOrDefault("on_error", "stop").toLowerCase(Locale.ROOT);
        if (!onError.equals("stop") && !onError.equals("continue")) {
            diagnostics.add(ScriptDiagnostic.error(1, 1, "on_error must be stop or continue"));
        }
        String onDenied = metadata.getOrDefault("on_denied", "stop").toLowerCase(Locale.ROOT);
        if (!onDenied.equals("stop") && !onDenied.equals("skip")) {
            diagnostics.add(ScriptDiagnostic.error(1, 1, "on_denied must be stop or skip"));
        }
        metadata.put("on_error", onError);
        metadata.put("on_denied", onDenied);
        return metadata;
    }

    private static List<CompiledScript.ScriptArgument> compileArguments(
            List<ScriptAst.Argument> arguments, List<ScriptDiagnostic> diagnostics) {
        List<CompiledScript.ScriptArgument> result = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (ScriptAst.Argument argument : arguments) {
            if (!names.add(argument.name())) {
                diagnostics.add(ScriptDiagnostic.error(argument.line(), 1,
                        "Duplicate argument '" + argument.name() + "'"));
            }
            String type = argument.type().toLowerCase(Locale.ROOT);
            if (!ARGUMENT_TYPES.contains(type)) {
                diagnostics.add(ScriptDiagnostic.error(argument.line(), 1,
                        "Unknown argument type '" + argument.type() + "'"));
            }
            result.add(new CompiledScript.ScriptArgument(argument.name(), type, argument.defaultValue(),
                    argument.min(), argument.max(), argument.line()));
        }
        return result;
    }

    private static void validateExpressions(List<Stmt> statements, List<ScriptDiagnostic> diagnostics) {
        for (Stmt statement : statements) {
            validateStatement(statement, diagnostics);
        }
    }

    private static void validateStatement(Stmt statement, List<ScriptDiagnostic> diagnostics) {
        if (statement instanceof Stmt.Let let) {
            validateExpression(let.value(), diagnostics);
        } else if (statement instanceof Stmt.Set set) {
            validateExpression(set.value(), diagnostics);
        } else if (statement instanceof Stmt.If conditional) {
            validateExpression(conditional.condition(), diagnostics);
            conditional.thenBranch().forEach(child -> validateStatement(child, diagnostics));
            conditional.elseBranch().forEach(child -> validateStatement(child, diagnostics));
        } else if (statement instanceof Stmt.For loop) {
            validateExpression(loop.start(), diagnostics);
            validateExpression(loop.end(), diagnostics);
            if (loop.step() != null) {
                validateExpression(loop.step(), diagnostics);
            }
            loop.body().forEach(child -> validateStatement(child, diagnostics));
        } else if (statement instanceof Stmt.Run run) {
            validateTemplate(run.command(), diagnostics);
        } else if (statement instanceof Stmt.Log log) {
            validateTemplate(log.message(), diagnostics);
        } else if (statement instanceof Stmt.Fail fail) {
            validateTemplate(fail.message(), diagnostics);
        }
    }

    private static void validateTemplate(Template template, List<ScriptDiagnostic> diagnostics) {
        for (Template.Part part : template.parts()) {
            if (part instanceof Template.ExpressionPart expression) {
                validateExpression(expression.expression(), diagnostics);
            }
        }
    }

    private static void validateExpression(Expr expression, List<ScriptDiagnostic> diagnostics) {
        if (expression instanceof Expr.Call call) {
            if (!BUILTINS.contains(call.name())) {
                diagnostics.add(ScriptDiagnostic.error(call.line(), call.column(),
                        "Unknown function '" + call.name() + "'"));
            }
            call.arguments().forEach(argument -> validateExpression(argument, diagnostics));
        } else if (expression instanceof Expr.Binary binary) {
            validateExpression(binary.left(), diagnostics);
            validateExpression(binary.right(), diagnostics);
        } else if (expression instanceof Expr.Unary unary) {
            validateExpression(unary.operand(), diagnostics);
        } else if (expression instanceof Expr.TemplateExpr template) {
            validateTemplate(template.template(), diagnostics);
        }
    }

    public static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class InstructionBuilder {
        private final List<ScriptDiagnostic> diagnostics;
        private final List<CompiledScript.Instruction> output = new ArrayList<>();
        private int hiddenCounter;

        private InstructionBuilder(List<ScriptDiagnostic> diagnostics) {
            this.diagnostics = diagnostics;
        }

        private List<CompiledScript.Instruction> compile(List<Stmt> statements) {
            statements.forEach(this::emit);
            return output;
        }

        private void emit(Stmt statement) {
            if (statement instanceof Stmt.Let let) {
                add(new CompiledScript.Push(let.value(), let.line()));
                add(new CompiledScript.Declare(let.name(), let.line()));
            } else if (statement instanceof Stmt.Set set) {
                add(new CompiledScript.Push(set.value(), set.line()));
                add(new CompiledScript.Store(set.name(), set.line()));
            } else if (statement instanceof Stmt.If conditional) {
                add(new CompiledScript.Push(conditional.condition(), conditional.line()));
                int falseJump = add(new CompiledScript.JumpIfFalse(-1, conditional.line()));
                conditional.thenBranch().forEach(this::emit);
                int endJump = add(new CompiledScript.Jump(-1, conditional.line()));
                patch(falseJump, output.size());
                conditional.elseBranch().forEach(this::emit);
                patch(endJump, output.size());
            } else if (statement instanceof Stmt.For loop) {
                emitFor(loop);
            } else if (statement instanceof Stmt.Wait wait) {
                add(new CompiledScript.Wait(wait.ticks(), wait.line()));
            } else if (statement instanceof Stmt.Run run) {
                add(new CompiledScript.RunCommand(run.command(), run.line()));
            } else if (statement instanceof Stmt.Log log) {
                add(new CompiledScript.Log(log.message(), log.line()));
            } else if (statement instanceof Stmt.Fail fail) {
                add(new CompiledScript.Fail(fail.message(), fail.line()));
            } else if (statement instanceof Stmt.Return returned) {
                add(new CompiledScript.Return(returned.line()));
            }
        }

        private void emitFor(Stmt.For loop) {
            String suffix = Integer.toString(hiddenCounter++);
            String startName = "__for_start_" + suffix;
            String endName = "__for_end_" + suffix;
            String stepName = "__for_step_" + suffix;
            add(new CompiledScript.Push(loop.start(), loop.line()));
            add(new CompiledScript.Declare(startName, loop.line()));
            add(new CompiledScript.Push(loop.end(), loop.line()));
            add(new CompiledScript.Declare(endName, loop.line()));
            if (loop.step() == null) {
                add(new CompiledScript.Push(new Expr.Literal(new Value.IntValue(1), loop.line(), 1), loop.line()));
            } else {
                add(new CompiledScript.Push(loop.step(), loop.line()));
            }
            add(new CompiledScript.Declare(stepName, loop.line()));
            add(new CompiledScript.Push(new Expr.Variable(startName, loop.line(), 1), loop.line()));
            add(new CompiledScript.Declare(loop.variable(), loop.line()));

            int loopStart = output.size();
            add(new CompiledScript.Push(new Expr.Call("__for_continue",
                    List.of(new Expr.Variable(loop.variable(), loop.line(), 1),
                            new Expr.Variable(endName, loop.line(), 1),
                            new Expr.Variable(stepName, loop.line(), 1)),
                    loop.line(), 1), loop.line()));
            int exitJump = add(new CompiledScript.JumpIfFalse(-1, loop.line()));
            loop.body().forEach(this::emit);
            add(new CompiledScript.Push(new Expr.Binary(new Expr.Variable(loop.variable(), loop.line(), 1), "+",
                    new Expr.Variable(stepName, loop.line(), 1), loop.line(), 1), loop.line()));
            add(new CompiledScript.Store(loop.variable(), loop.line()));
            add(new CompiledScript.Jump(loopStart, loop.line()));
            patch(exitJump, output.size());
        }

        private int add(CompiledScript.Instruction instruction) {
            output.add(instruction);
            if (output.size() > MAX_INSTRUCTIONS) {
                diagnostics.add(ScriptDiagnostic.error(instruction.line(), 1,
                        "Script exceeds " + MAX_INSTRUCTIONS + " instructions"));
            }
            return output.size() - 1;
        }

        private void patch(int index, int target) {
            CompiledScript.Instruction instruction = output.get(index);
            if (instruction instanceof CompiledScript.Jump jump) {
                output.set(index, new CompiledScript.Jump(target, jump.line()));
            } else if (instruction instanceof CompiledScript.JumpIfFalse jump) {
                output.set(index, new CompiledScript.JumpIfFalse(target, jump.line()));
            }
        }
    }
}
