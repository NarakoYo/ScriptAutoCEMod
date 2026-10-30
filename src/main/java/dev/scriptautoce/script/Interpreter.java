package dev.scriptautoce.script;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Interpreter {
    public static final int MAX_EXECUTED_OPERATIONS = 1_000_000;

    private final CompiledScript script;
    private final ScriptHost host;
    private final Map<String, Value> variables = new LinkedHashMap<>();
    private final Deque<Value> stack = new ArrayDeque<>();
    private final Deque<String> logs = new ArrayDeque<>();
    private final Gson gson = new Gson();
    private int pc;
    private int executedOperations;
    private boolean finished;

    public Interpreter(CompiledScript script, Map<String, String> suppliedArguments, ScriptHost host) {
        this.script = script;
        this.host = host;
        initializeArguments(suppliedArguments);
    }

    public StepResult advance() {
        if (finished) {
            return new Finished("completed", "Script finished");
        }
        int localOperations = 0;
        while (pc < script.instructions().size()) {
            if (++localOperations > 100_000 || ++executedOperations > MAX_EXECUTED_OPERATIONS) {
                throw new ScriptException(currentLine(), 1, "Execution operation limit exceeded");
            }
            CompiledScript.Instruction instruction = script.instructions().get(pc);
            if (instruction instanceof CompiledScript.Push push) {
                stack.push(evaluate(push.expression()));
                pc++;
            } else if (instruction instanceof CompiledScript.Declare declare) {
                if (variables.containsKey(declare.name())) {
                    throw new ScriptException(declare.line(), 1,
                            "Variable '" + declare.name() + "' is already defined");
                }
                variables.put(declare.name(), pop(declare.line()));
                pc++;
            } else if (instruction instanceof CompiledScript.Store store) {
                if (!variables.containsKey(store.name())) {
                    throw new ScriptException(store.line(), 1,
                            "Variable '" + store.name() + "' is not defined");
                }
                variables.put(store.name(), pop(store.line()));
                pc++;
            } else if (instruction instanceof CompiledScript.Pop pop) {
                pop(pop.line());
                pc++;
            } else if (instruction instanceof CompiledScript.Jump jump) {
                pc = jump.target();
            } else if (instruction instanceof CompiledScript.JumpIfFalse jump) {
                Value condition = pop(jump.line());
                if (!condition.asBoolean()) {
                    pc = jump.target();
                } else {
                    pc++;
                }
            } else if (instruction instanceof CompiledScript.RunCommand run) {
                String command = render(run.command(), true);
                pc++;
                return new Command(command, run.line());
            } else if (instruction instanceof CompiledScript.Log log) {
                logs.add(render(log.message(), false));
                pc++;
            } else if (instruction instanceof CompiledScript.Wait wait) {
                pc++;
                return new Waiting(wait.ticks(), wait.line());
            } else if (instruction instanceof CompiledScript.Fail fail) {
                String message = render(fail.message(), false);
                finished = true;
                throw new ScriptException(fail.line(), 1, message);
            } else if (instruction instanceof CompiledScript.Return returned) {
                finished = true;
                return new Finished("completed", "Script returned");
            }
        }
        finished = true;
        return new Finished("completed", "Script finished");
    }

    public List<String> drainLogs() {
        List<String> result = new ArrayList<>(logs);
        logs.clear();
        return result;
    }

    public Map<String, Value> variables() {
        return Map.copyOf(variables);
    }

    public boolean finished() {
        return finished;
    }

    private void initializeArguments(Map<String, String> supplied) {
        for (CompiledScript.ScriptArgument argument : script.arguments()) {
            Value value;
            if (supplied.containsKey(argument.name())) {
                value = parseSupplied(argument, supplied.get(argument.name()));
            } else {
                value = evaluate(argument.defaultValue());
                value = coerce(argument, value);
            }
            validateRange(argument, value);
            variables.put(argument.name(), value);
        }
    }

    private Value parseSupplied(CompiledScript.ScriptArgument argument, String raw) {
        try {
            return switch (argument.type()) {
                case "int" -> new Value.IntValue(Long.parseLong(raw.trim()));
                case "double" -> new Value.DoubleValue(Double.parseDouble(raw.trim()));
                case "bool" -> new Value.BoolValue(parseBoolean(raw));
                case "string" -> new Value.StringValue(raw);
                case "token" -> new Value.TokenValue(raw.trim());
                case "selector" -> new Value.SelectorValue(raw.trim());
                case "vec3" -> {
                    String[] parts = raw.trim().split("[,\\s]+");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("vec3 requires x,y,z");
                    }
                    yield new Value.Vec3Value(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]));
                }
                default -> throw new IllegalArgumentException("Unknown argument type");
            };
        } catch (RuntimeException exception) {
            throw new ScriptException(argument.line(), 1,
                    "Invalid value for argument '" + argument.name() + "': " + exception.getMessage());
        }
    }

    private Value coerce(CompiledScript.ScriptArgument argument, Value value) {
        if (value instanceof Value.StringValue string) {
            return parseSupplied(argument, string.value());
        }
        return switch (argument.type()) {
            case "int" -> new Value.IntValue(value.asLong());
            case "double" -> new Value.DoubleValue(value.asDouble());
            case "bool" -> new Value.BoolValue(value.asBoolean());
            case "string" -> new Value.StringValue(value.display());
            case "token" -> new Value.TokenValue(value.display());
            case "selector" -> new Value.SelectorValue(value.display());
            case "vec3" -> {
                if (value instanceof Value.Vec3Value vec3) {
                    yield vec3;
                }
                throw new ScriptException(argument.line(), 1, "Expected vec3");
            }
            default -> throw new ScriptException(argument.line(), 1, "Unknown argument type");
        };
    }

    private void validateRange(CompiledScript.ScriptArgument argument, Value value) {
        if (!(value instanceof Value.IntValue) && !(value instanceof Value.DoubleValue)) {
            return;
        }
        double numeric = value.asDouble();
        if (argument.min() != null && numeric < evaluate(argument.min()).asDouble()) {
            throw new ScriptException(argument.line(), 1,
                    "Argument '" + argument.name() + "' is below its minimum");
        }
        if (argument.max() != null && numeric > evaluate(argument.max()).asDouble()) {
            throw new ScriptException(argument.line(), 1,
                    "Argument '" + argument.name() + "' is above its maximum");
        }
    }

    private Value evaluate(Expr expression) {
        if (expression instanceof Expr.Literal literal) {
            return literal.value();
        }
        if (expression instanceof Expr.Variable variable) {
            Value value = variables.get(variable.name());
            if (value == null) {
                throw new ScriptException(variable.line(), variable.column(),
                        "Unknown variable '" + variable.name() + "'");
            }
            return value;
        }
        if (expression instanceof Expr.TemplateExpr template) {
            return new Value.StringValue(render(template.template(), false));
        }
        if (expression instanceof Expr.Unary unary) {
            Value operand = evaluate(unary.operand());
            return switch (unary.operator()) {
                case "!" -> new Value.BoolValue(!operand.asBoolean());
                case "-" -> operand instanceof Value.IntValue
                        ? new Value.IntValue(-operand.asLong())
                        : new Value.DoubleValue(-operand.asDouble());
                default -> throw new ScriptException(unary.line(), unary.column(), "Unknown unary operator");
            };
        }
        if (expression instanceof Expr.Binary binary) {
            if (binary.operator().equals("&&")) {
                Value left = evaluate(binary.left());
                return new Value.BoolValue(left.asBoolean() && evaluate(binary.right()).asBoolean());
            }
            if (binary.operator().equals("||")) {
                Value left = evaluate(binary.left());
                return new Value.BoolValue(left.asBoolean() || evaluate(binary.right()).asBoolean());
            }
            Value left = evaluate(binary.left());
            Value right = evaluate(binary.right());
            return evaluateBinary(binary, left, right);
        }
        if (expression instanceof Expr.Call call) {
            List<Value> arguments = call.arguments().stream().map(this::evaluate).toList();
            return evaluateCall(call, arguments);
        }
        throw new ScriptException(expression.line(), 1, "Unsupported expression");
    }

    private Value evaluateBinary(Expr.Binary expression, Value left, Value right) {
        return switch (expression.operator()) {
            case "+" -> {
                if (left instanceof Value.StringValue || right instanceof Value.StringValue) {
                    yield new Value.StringValue(left.display() + right.display());
                }
                if (left instanceof Value.Vec3Value a && right instanceof Value.Vec3Value b) {
                    yield new Value.Vec3Value(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
                }
                yield numeric(left, right, expression.line(), expression.column(), true,
                        (a, b) -> a + b);
            }
            case "-" -> numeric(left, right, expression.line(), expression.column(), false,
                    (a, b) -> a - b);
            case "*" -> numeric(left, right, expression.line(), expression.column(), false,
                    (a, b) -> a * b);
            case "/" -> {
                if (right.asDouble() == 0) {
                    throw new ScriptException(expression.line(), expression.column(), "Division by zero");
                }
                yield numeric(left, right, expression.line(), expression.column(), false,
                        (a, b) -> a / b);
            }
            case "%" -> {
                if (right.asDouble() == 0) {
                    throw new ScriptException(expression.line(), expression.column(), "Division by zero");
                }
                yield numeric(left, right, expression.line(), expression.column(), true,
                        (a, b) -> a % b);
            }
            case "==" -> new Value.BoolValue(equalValues(left, right));
            case "!=" -> new Value.BoolValue(!equalValues(left, right));
            case "<" -> new Value.BoolValue(left.asDouble() < right.asDouble());
            case "<=" -> new Value.BoolValue(left.asDouble() <= right.asDouble());
            case ">" -> new Value.BoolValue(left.asDouble() > right.asDouble());
            case ">=" -> new Value.BoolValue(left.asDouble() >= right.asDouble());
            default -> throw new ScriptException(expression.line(), expression.column(), "Unknown operator");
        };
    }

    private Value numeric(Value left, Value right, int line, int column, boolean preferInteger,
                          BinaryNumberOperator operator) {
        double result = operator.apply(left.asDouble(), right.asDouble());
        if (preferInteger && left instanceof Value.IntValue && right instanceof Value.IntValue) {
            return new Value.IntValue(Math.round(result));
        }
        return new Value.DoubleValue(result);
    }

    private Value evaluateCall(Expr.Call call, List<Value> arguments) {
        switch (call.name()) {
            case "pos" -> {
                requireArity(call, arguments, 0);
                ScriptHost.Position position = host.position();
                return new Value.Vec3Value(position.x(), position.y(), position.z());
            }
            case "block" -> {
                requireArity(call, arguments, 3);
                return new Value.TokenValue(host.blockAt(arguments.get(0).display(),
                        arguments.get(1).display(), arguments.get(2).display()));
            }
            case "players" -> {
                requireArity(call, arguments, 0);
                return new Value.ListValue(host.players().stream().map(Value.StringValue::new).map(Value.class::cast).toList());
            }
            case "entities" -> {
                requireArity(call, arguments, 2);
                return new Value.ListValue(host.entities(arguments.get(0).display(), arguments.get(1).asDouble())
                        .stream().map(Value.StringValue::new).map(Value.class::cast).toList());
            }
            case "score" -> {
                requireArity(call, arguments, 2);
                return new Value.IntValue(host.score(arguments.get(0).display(), arguments.get(1).display()));
            }
            case "token" -> {
                requireArity(call, arguments, 1);
                return new Value.TokenValue(arguments.getFirst().display());
            }
            case "quote" -> {
                requireArity(call, arguments, 1);
                return new Value.EncodedValue(quote(arguments.getFirst().display()));
            }
            case "json" -> {
                requireArity(call, arguments, 1);
                return new Value.EncodedValue(gson.toJson(arguments.getFirst().display()));
            }
            case "min" -> {
                requireArity(call, arguments, 2);
                return new Value.DoubleValue(Math.min(arguments.get(0).asDouble(), arguments.get(1).asDouble()));
            }
            case "max" -> {
                requireArity(call, arguments, 2);
                return new Value.DoubleValue(Math.max(arguments.get(0).asDouble(), arguments.get(1).asDouble()));
            }
            case "abs" -> {
                requireArity(call, arguments, 1);
                Value value = arguments.getFirst();
                return value instanceof Value.IntValue
                        ? new Value.IntValue(Math.abs(value.asLong()))
                        : new Value.DoubleValue(Math.abs(value.asDouble()));
            }
            case "round" -> {
                requireArity(call, arguments, 1);
                return new Value.IntValue(Math.round(arguments.getFirst().asDouble()));
            }
            case "__for_continue" -> {
                requireArity(call, arguments, 3);
                double current = arguments.get(0).asDouble();
                double end = arguments.get(1).asDouble();
                double step = arguments.get(2).asDouble();
                if (step == 0) {
                    throw new ScriptException(call.line(), call.column(), "Loop step cannot be zero");
                }
                return new Value.BoolValue(step > 0 ? current < end : current > end);
            }
            default -> throw new ScriptException(call.line(), call.column(),
                    "Unknown function '" + call.name() + "'");
        }
    }

    private static void requireArity(Expr.Call call, List<Value> arguments, int expected) {
        if (arguments.size() != expected) {
            throw new ScriptException(call.line(), call.column(),
                    call.name() + "() expects " + expected + " arguments");
        }
    }

    private String render(Template template, boolean commandMode) {
        StringBuilder result = new StringBuilder();
        for (Template.Part part : template.parts()) {
            if (part instanceof Template.TextPart text) {
                result.append(text.value());
            } else if (part instanceof Template.ExpressionPart expression) {
                Value value = evaluate(expression.expression());
                result.append(commandMode ? value.commandToken() : value.display());
            }
        }
        return result.toString();
    }

    private Value pop(int line) {
        if (stack.isEmpty()) {
            throw new ScriptException(line, 1, "Internal stack underflow");
        }
        return stack.pop();
    }

    private int currentLine() {
        if (pc >= 0 && pc < script.instructions().size()) {
            return script.instructions().get(pc).line();
        }
        return 1;
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true")) {
            return true;
        }
        if (normalized.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false");
    }

    private static boolean equalValues(Value left, Value right) {
        if (left instanceof Value.IntValue && right instanceof Value.IntValue) {
            return left.asLong() == right.asLong();
        }
        if (left instanceof Value.IntValue || left instanceof Value.DoubleValue
                || right instanceof Value.IntValue || right instanceof Value.DoubleValue) {
            return left.asDouble() == right.asDouble();
        }
        return left.display().equals(right.display());
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @FunctionalInterface
    private interface BinaryNumberOperator {
        double apply(double left, double right);
    }

    public sealed interface StepResult permits Command, Waiting, Finished {
    }

    public record Command(String command, int line) implements StepResult {
    }

    public record Waiting(int ticks, int line) implements StepResult {
    }

    public record Finished(String status, String message) implements StepResult {
    }
}
