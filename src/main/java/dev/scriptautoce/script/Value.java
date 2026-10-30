package dev.scriptautoce.script;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public sealed interface Value permits Value.IntValue, Value.DoubleValue, Value.BoolValue,
        Value.StringValue, Value.TokenValue, Value.EncodedValue, Value.Vec3Value,
        Value.SelectorValue, Value.ListValue {
    String display();

    default long asLong() {
        throw new ScriptException(0, 0, "Expected a whole number, got " + display());
    }

    default double asDouble() {
        throw new ScriptException(0, 0, "Expected a number, got " + display());
    }

    default boolean asBoolean() {
        throw new ScriptException(0, 0, "Expected true or false, got " + display());
    }

    default String asString() {
        throw new ScriptException(0, 0, "Expected text, got " + display());
    }

    default String commandToken() {
        throw new ScriptException(0, 0, "This value cannot be inserted into a command; use quote() or json()");
    }

    record IntValue(long value) implements Value {
        @Override
        public String display() {
            return Long.toString(value);
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public String commandToken() {
            return display();
        }
    }

    record DoubleValue(double value) implements Value {
        @Override
        public String display() {
            return String.format(Locale.ROOT, "%.6f", value).replaceFirst("0+$", "").replaceFirst("\\.$", "");
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public String commandToken() {
            return display();
        }
    }

    record BoolValue(boolean value) implements Value {
        @Override
        public String display() {
            return Boolean.toString(value);
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public String commandToken() {
            return display();
        }
    }

    record StringValue(String value) implements Value {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String display() {
            return value;
        }

        @Override
        public String asString() {
            return value;
        }
    }

    record TokenValue(String value) implements Value {
        public TokenValue {
            Objects.requireNonNull(value, "value");
            if (!value.matches("[A-Za-z0-9_.:+/=-]+")) {
                throw new ScriptException(0, 0, "Invalid safe command token: " + value);
            }
        }

        @Override
        public String display() {
            return value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String commandToken() {
            return value;
        }
    }

    record EncodedValue(String value) implements Value {
        public EncodedValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String display() {
            return value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String commandToken() {
            return value;
        }
    }

    record Vec3Value(double x, double y, double z) implements Value {
        @Override
        public String display() {
            return new DoubleValue(x).display() + " " + new DoubleValue(y).display() + " "
                    + new DoubleValue(z).display();
        }

        @Override
        public String commandToken() {
            return display();
        }
    }

    record SelectorValue(String value) implements Value {
        public SelectorValue {
            Objects.requireNonNull(value, "value");
            if (!value.matches("@[aeprs](\\[[^\\r\\n]*])?")) {
                throw new ScriptException(0, 0, "Invalid entity selector");
            }
        }

        @Override
        public String display() {
            return value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String commandToken() {
            return value;
        }
    }

    record ListValue(List<Value> values) implements Value {
        public ListValue {
            values = List.copyOf(values);
        }

        @Override
        public String display() {
            return values.stream().map(Value::display).toList().toString();
        }
    }
}
