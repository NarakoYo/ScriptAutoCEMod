package dev.scriptautoce.script;

import java.util.ArrayList;
import java.util.List;

public record Template(List<Part> parts) {
    public Template {
        parts = List.copyOf(parts);
    }

    public static Template text(String text) {
        return new Template(List.of(new TextPart(text)));
    }

    public String staticText() {
        StringBuilder builder = new StringBuilder();
        for (Part part : parts) {
            if (!(part instanceof TextPart text)) {
                return null;
            }
            builder.append(text.value());
        }
        return builder.toString();
    }

    public sealed interface Part permits TextPart, ExpressionPart {
    }

    public record TextPart(String value) implements Part {
    }

    public record ExpressionPart(Expr expression) implements Part {
    }

    static Template parse(String value, int line, int column) {
        List<Part> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int interpolation = value.indexOf("${", cursor);
            if (interpolation < 0) {
                text.append(value, cursor, value.length());
                break;
            }
            text.append(value, cursor, interpolation);
            if (!text.isEmpty()) {
                parts.add(new TextPart(text.toString()));
                text.setLength(0);
            }
            int end = value.indexOf('}', interpolation + 2);
            if (end < 0) {
                throw new ScriptException(line, column, "Unclosed interpolation");
            }
            String expression = value.substring(interpolation + 2, end);
            if (expression.isBlank()) {
                throw new ScriptException(line, column, "Empty interpolation");
            }
            parts.add(new ExpressionPart(Parser.parseStandaloneExpression(expression, line, column)));
            cursor = end + 1;
        }
        if (!text.isEmpty()) {
            parts.add(new TextPart(text.toString()));
        }
        if (parts.isEmpty()) {
            parts.add(new TextPart(""));
        }
        return new Template(parts);
    }
}
