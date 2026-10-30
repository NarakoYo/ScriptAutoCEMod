package dev.scriptautoce.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SemanticValidator {
    private enum Type {
        INT,
        DOUBLE,
        BOOL,
        STRING,
        TOKEN,
        VEC3,
        SELECTOR,
        LIST,
        ENCODED,
        UNKNOWN
    }

    private final List<ScriptDiagnostic> diagnostics;
    private final Map<String, Type> variables = new LinkedHashMap<>();

    private SemanticValidator(List<ScriptDiagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    static void validate(ScriptAst ast, List<ScriptDiagnostic> diagnostics) {
        SemanticValidator validator = new SemanticValidator(diagnostics);
        ast.arguments().forEach(argument -> validator.variables.put(
                argument.name(), argumentType(argument.type())));
        validator.validateStatements(ast.statements());
    }

    private void validateStatements(List<Stmt> statements) {
        for (Stmt statement : statements) {
            validateStatement(statement);
        }
    }

    private void validateStatement(Stmt statement) {
        if (statement instanceof Stmt.Let let) {
            if (variables.containsKey(let.name())) {
                error(let.line(), 1, "Variable '" + let.name() + "' is already defined");
                infer(let.value());
                return;
            }
            Type type = infer(let.value());
            variables.put(let.name(), type);
        } else if (statement instanceof Stmt.Set set) {
            if (!variables.containsKey(set.name())) {
                error(set.line(), 1, "Variable '" + set.name() + "' is not defined");
            }
            Type type = infer(set.value());
            if (variables.containsKey(set.name())) {
                variables.put(set.name(), type);
            }
        } else if (statement instanceof Stmt.If conditional) {
            requireType(infer(conditional.condition()), Type.BOOL, conditional.line(), 1,
                    "if condition must be a boolean");
            validateBranch(conditional.thenBranch(), conditional.elseBranch());
        } else if (statement instanceof Stmt.For loop) {
            requireNumeric(infer(loop.start()), loop.line(), "Loop start must be numeric");
            requireNumeric(infer(loop.end()), loop.line(), "Loop end must be numeric");
            if (loop.step() != null) {
                requireNumeric(infer(loop.step()), loop.line(), "Loop step must be numeric");
                if (loop.step() instanceof Expr.Literal literal
                        && literal.value() instanceof Value.IntValue integer
                        && integer.value() == 0) {
                    error(loop.line(), 1, "Loop step cannot be zero");
                }
            }
            Map<String, Type> before = new LinkedHashMap<>(variables);
            if (variables.containsKey(loop.variable())) {
                error(loop.line(), 1, "Loop variable '" + loop.variable() + "' is already defined");
            }
            variables.put(loop.variable(), Type.INT);
            validateStatements(loop.body());
            Map<String, Type> after = new LinkedHashMap<>(variables);
            variables.clear();
            variables.putAll(before);
            for (Map.Entry<String, Type> entry : before.entrySet()) {
                if (after.containsKey(entry.getKey())) {
                    variables.put(entry.getKey(), after.get(entry.getKey()));
                }
            }
            variables.put(loop.variable(), Type.INT);
        } else if (statement instanceof Stmt.Run run) {
            String staticCommand = run.command().staticText();
            if (staticCommand != null && staticCommand.isBlank()) {
                error(run.line(), 1, "run command cannot be empty");
            }
            validateTemplate(run.command(), true);
        } else if (statement instanceof Stmt.Log log) {
            validateTemplate(log.message(), false);
        } else if (statement instanceof Stmt.Fail fail) {
            validateTemplate(fail.message(), false);
        }
    }

    private void validateBranch(List<Stmt> thenBranch, List<Stmt> elseBranch) {
        Map<String, Type> before = new LinkedHashMap<>(variables);
        Map<String, Type> thenVariables = new LinkedHashMap<>(before);
        variables.clear();
        variables.putAll(thenVariables);
        validateStatements(thenBranch);
        Map<String, Type> afterThen = new LinkedHashMap<>(variables);

        variables.clear();
        variables.putAll(before);
        validateStatements(elseBranch);
        Map<String, Type> afterElse = new LinkedHashMap<>(variables);

        variables.clear();
        variables.putAll(before);
        for (Map.Entry<String, Type> entry : afterThen.entrySet()) {
            if (afterElse.containsKey(entry.getKey())) {
                Type other = afterElse.get(entry.getKey());
                variables.put(entry.getKey(), entry.getValue() == other ? entry.getValue() : Type.UNKNOWN);
            } else if (before.containsKey(entry.getKey())) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Type> entry : afterElse.entrySet()) {
            if (before.containsKey(entry.getKey()) && variables.containsKey(entry.getKey())) {
                Type other = afterThen.get(entry.getKey());
                if (other != null && other != entry.getValue()) {
                    variables.put(entry.getKey(), Type.UNKNOWN);
                }
            }
        }
    }

    private void validateTemplate(Template template, boolean commandMode) {
        for (Template.Part part : template.parts()) {
            if (!(part instanceof Template.ExpressionPart expressionPart)) {
                continue;
            }
            Type type = infer(expressionPart.expression());
            if (commandMode && (type == Type.STRING || type == Type.LIST)) {
                error(expressionPart.expression().line(), 1,
                        "String and list values must be encoded with quote(), json() or token() "
                                + "before use in run");
            }
        }
    }

    private Type infer(Expr expression) {
        if (expression instanceof Expr.Literal literal) {
            return valueType(literal.value());
        }
        if (expression instanceof Expr.Variable variable) {
            Type type = variables.get(variable.name());
            if (type == null) {
                error(variable.line(), variable.column(),
                        "Unknown variable '" + variable.name() + "'");
                return Type.UNKNOWN;
            }
            return type;
        }
        if (expression instanceof Expr.TemplateExpr template) {
            validateTemplate(template.template(), false);
            return Type.STRING;
        }
        if (expression instanceof Expr.Unary unary) {
            Type operand = infer(unary.operand());
            if (unary.operator().equals("!")) {
                requireType(operand, Type.BOOL, unary.line(), unary.column(),
                        "! requires a boolean");
                return Type.BOOL;
            }
            requireNumeric(operand, unary.line(), "Unary - requires a number");
            return operand == Type.INT ? Type.INT : Type.DOUBLE;
        }
        if (expression instanceof Expr.Binary binary) {
            return inferBinary(binary);
        }
        if (expression instanceof Expr.Call call) {
            return inferCall(call);
        }
        return Type.UNKNOWN;
    }

    private Type inferBinary(Expr.Binary binary) {
        Type left = infer(binary.left());
        Type right = infer(binary.right());
        return switch (binary.operator()) {
            case "&&", "||" -> {
                requireType(left, Type.BOOL, binary.line(), binary.column(),
                        binary.operator() + " requires booleans");
                requireType(right, Type.BOOL, binary.line(), binary.column(),
                        binary.operator() + " requires booleans");
                yield Type.BOOL;
            }
            case "==", "!=" -> Type.BOOL;
            case "<", "<=", ">", ">=" -> {
                requireNumeric(left, binary.line(), "Comparison requires numbers");
                requireNumeric(right, binary.line(), "Comparison requires numbers");
                yield Type.BOOL;
            }
            case "+" -> {
                if (left == Type.STRING || right == Type.STRING) {
                    yield Type.STRING;
                }
                if (left == Type.VEC3 && right == Type.VEC3) {
                    yield Type.VEC3;
                }
                yield numericResult(left, right, binary.line(), binary.column(), "+");
            }
            case "-", "*", "/", "%" -> numericResult(left, right,
                    binary.line(), binary.column(), binary.operator());
            default -> Type.UNKNOWN;
        };
    }

    private Type numericResult(Type left, Type right, int line, int column, String operator) {
        requireNumeric(left, line, operator + " requires numbers");
        requireNumeric(right, line, operator + " requires numbers");
        if (left == Type.UNKNOWN || right == Type.UNKNOWN) {
            return Type.UNKNOWN;
        }
        if (operator.equals("%") || operator.equals("+") || operator.equals("-")
                || operator.equals("*")) {
            return left == Type.INT && right == Type.INT ? Type.INT : Type.DOUBLE;
        }
        return Type.DOUBLE;
    }

    private Type inferCall(Expr.Call call) {
        call.arguments().forEach(this::infer);
        Integer expectedArity = ScriptCompiler.BUILTIN_ARITY.get(call.name());
        if (expectedArity == null) {
            error(call.line(), call.column(), "Unknown function '" + call.name() + "'");
            return Type.UNKNOWN;
        }
        if (call.arguments().size() != expectedArity) {
            error(call.line(), call.column(),
                    call.name() + "() expects " + expectedArity + " arguments");
        }
        return switch (call.name()) {
            case "pos" -> Type.VEC3;
            case "block", "token" -> Type.TOKEN;
            case "players", "entities" -> Type.LIST;
            case "score", "round", "floor", "ceil", "length" -> Type.INT;
            case "quote", "json" -> Type.ENCODED;
            case "min", "max" -> Type.DOUBLE;
            case "abs", "clamp" -> Type.UNKNOWN;
            case "contains", "startsWith", "endsWith" -> Type.BOOL;
            case "lower", "upper", "trim", "replace", "join" -> Type.STRING;
            default -> Type.UNKNOWN;
        };
    }

    private void requireType(Type actual, Type expected, int line, int column, String message) {
        if (actual != Type.UNKNOWN && actual != expected) {
            error(line, column, message);
        }
    }

    private void requireNumeric(Type actual, int line, String message) {
        if (actual != Type.UNKNOWN && actual != Type.INT && actual != Type.DOUBLE) {
            error(line, 1, message);
        }
    }

    private void error(int line, int column, String message) {
        diagnostics.add(ScriptDiagnostic.error(line, Math.max(1, column), message));
    }

    private static Type argumentType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "int" -> Type.INT;
            case "double" -> Type.DOUBLE;
            case "bool" -> Type.BOOL;
            case "string" -> Type.STRING;
            case "token" -> Type.TOKEN;
            case "vec3" -> Type.VEC3;
            case "selector" -> Type.SELECTOR;
            default -> Type.UNKNOWN;
        };
    }

    private static Type valueType(Value value) {
        if (value instanceof Value.IntValue) {
            return Type.INT;
        }
        if (value instanceof Value.DoubleValue) {
            return Type.DOUBLE;
        }
        if (value instanceof Value.BoolValue) {
            return Type.BOOL;
        }
        if (value instanceof Value.StringValue) {
            return Type.STRING;
        }
        if (value instanceof Value.TokenValue) {
            return Type.TOKEN;
        }
        if (value instanceof Value.Vec3Value) {
            return Type.VEC3;
        }
        if (value instanceof Value.SelectorValue) {
            return Type.SELECTOR;
        }
        if (value instanceof Value.ListValue) {
            return Type.LIST;
        }
        if (value instanceof Value.EncodedValue) {
            return Type.ENCODED;
        }
        return Type.UNKNOWN;
    }
}
