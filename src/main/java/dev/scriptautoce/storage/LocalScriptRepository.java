package dev.scriptautoce.storage;

import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.ScriptCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LocalScriptRepository {
    public record Summary(String id, String title, String fileName, String hash, long size) {
    }

    public List<Summary> list() {
        List<Summary> result = new ArrayList<>();
        try {
            Files.createDirectories(ConfigPaths.localScripts());
            try (var files = Files.list(ConfigPaths.localScripts())) {
                files.filter(path -> path.getFileName().toString().endsWith(".sace")).forEach(path -> {
                    try {
                        String source = Files.readString(path, StandardCharsets.UTF_8);
                        CompilationResult compilation = ScriptCompiler.compile(idOf(path), source);
                        String title = compilation.success()
                                ? compilation.script().title() : stripExtension(path.getFileName().toString());
                        result.add(new Summary(idOf(path), title, path.getFileName().toString(),
                                ScriptCompiler.sha256(source), Files.size(path)));
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparing(Summary::title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public String load(String id) {
        try {
            return Files.readString(pathFor(id), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Script not found: " + id);
        }
    }

    public String save(String id, String source) {
        String safeId = id == null || id.isBlank() ? uniqueId(source) : sanitizeId(id);
        try {
            Files.createDirectories(ConfigPaths.localScripts());
            Path destination = ConfigPaths.localScripts().resolve(safeId + ".sace");
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Could not save script", exception);
        }
        return safeId;
    }

    public String importSource(String preferredName, String source) {
        String id = preferredName == null ? uniqueId(source) : sanitizeId(preferredName);
        int suffix = 2;
        while (Files.exists(pathFor(id))) {
            id = sanitizeId(preferredName + "-" + suffix++);
        }
        return save(id, source);
    }

    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(pathFor(id));
        } catch (IOException exception) {
            return false;
        }
    }

    public Path pathFor(String id) {
        String safe = sanitizeId(id);
        return ConfigPaths.localScripts().resolve(safe + ".sace");
    }

    private static String uniqueId(String source) {
        String title = "script";
        CompilationResult compilation = ScriptCompiler.compile("new", source);
        if (compilation.success()) {
            title = compilation.script().title();
        }
        String base = sanitizeId(title);
        String result = base;
        int suffix = 2;
        while (Files.exists(ConfigPaths.localScripts().resolve(result + ".sace"))) {
            result = base + "-" + suffix++;
        }
        return result;
    }

    private static String sanitizeId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid script id");
        }
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (sanitized.isBlank()) {
            sanitized = "script";
        }
        return sanitized.substring(0, Math.min(64, sanitized.length()));
    }

    private static String idOf(Path path) {
        return stripExtension(path.getFileName().toString());
    }

    private static String stripExtension(String name) {
        return name.endsWith(".sace") ? name.substring(0, name.length() - 5) : name;
    }
}
