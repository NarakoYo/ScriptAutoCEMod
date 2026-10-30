package dev.scriptautoce.policy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandPolicy {
    public static final Set<String> DEFAULT_BLOCKED = Set.of(
            "stop", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "whitelist",
            "kick", "reload", "datapack", "function", "schedule", "jfr", "perf", "debug",
            "save-off", "save-on", "save-all", "tick", "transfer"
    );

    private final Set<String> blocked;
    private final Set<String> allowlist;

    public CommandPolicy(Set<String> blocked, Set<String> allowlist) {
        this.blocked = normalizeSet(blocked);
        this.allowlist = normalizeSet(allowlist);
    }

    public static CommandPolicy defaults() {
        return new CommandPolicy(DEFAULT_BLOCKED, Set.of());
    }

    public PolicyDecision evaluate(String command) {
        if (command == null || command.isBlank()) {
            return new PolicyDecision(false, true, "Command is empty");
        }
        String stripped = command.strip();
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        List<String> tokens = tokenize(stripped);
        if (tokens.isEmpty()) {
            return new PolicyDecision(false, true, "Command is empty");
        }
        String root = normalizeRoot(tokens.getFirst());
        if (!allowlist.isEmpty() && !allowlist.contains(root)) {
            return new PolicyDecision(false, true, "Command root '" + root + "' is not allowlisted");
        }
        if (blocked.contains(root) && !allowlist.contains(root)) {
            return new PolicyDecision(false, true, "Command root '" + root + "' is blocked by server policy");
        }
        if (root.equals("execute")) {
            for (int index = 1; index < tokens.size() - 1; index++) {
                if (!tokens.get(index).equals("run")) {
                    continue;
                }
                String nestedRoot = normalizeRoot(tokens.get(index + 1));
                if (!allowlist.isEmpty() && !allowlist.contains(nestedRoot)) {
                    return new PolicyDecision(false, true,
                            "Nested command root '" + nestedRoot + "' is not allowlisted");
                }
                if (blocked.contains(nestedRoot) && !allowlist.contains(nestedRoot)) {
                    return new PolicyDecision(false, true,
                            "Nested command root '" + nestedRoot + "' is blocked by server policy");
                }
            }
        }
        return new PolicyDecision(true, false, "Allowed");
    }

    public Set<String> blocked() {
        return Set.copyOf(blocked);
    }

    public Set<String> allowlist() {
        return Set.copyOf(allowlist);
    }

    static List<String> tokenize(String command) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < command.length(); index++) {
            char c = command.charAt(index);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                current.append(c);
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static Set<String> normalizeSet(Set<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(normalizeRoot(value));
            }
        }
        return Set.copyOf(result);
    }

    private static String normalizeRoot(String root) {
        String result = root.strip().toLowerCase(Locale.ROOT);
        int namespace = result.indexOf(':');
        return namespace >= 0 ? result.substring(namespace + 1) : result;
    }

    public record PolicyDecision(boolean allowed, boolean hardBlocked, String reason) {
    }
}
