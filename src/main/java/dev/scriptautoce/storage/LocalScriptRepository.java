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
import java.util.Set;

public final class LocalScriptRepository {
    private static final Set<Character> INVALID_NAME_CHARACTERS =
            Set.of('<', '>', ':', '"', '/', '\\', '|', '?', '*');
    private final Path root;

    public LocalScriptRepository() {
        this(ConfigPaths.localScripts());
    }

    public LocalScriptRepository(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Summary(
            String id,
            String title,
            String fileName,
            String relativePath,
            String folder,
            String hash,
            long size
    ) {
    }

    public List<Summary> list() {
        List<Summary> result = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sace"))
                        .forEach(path -> inspect(path, result));
            }
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparing(Summary::relativePath, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void inspect(Path path, List<Summary> result) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            String relativePath = relativePath(path);
            String id = stripExtension(relativePath);
            CompilationResult compilation = ScriptCompiler.compile(id, source);
            String title = compilation.success()
                    ? compilation.script().title()
                    : stripExtension(path.getFileName().toString());
            result.add(new Summary(id, title, path.getFileName().toString(), relativePath,
                    parentOf(relativePath), ScriptCompiler.sha256(source), Files.size(path)));
        } catch (IOException ignored) {
        }
    }

    public List<String> folders() {
        List<String> result = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (var directories = Files.walk(root)) {
                directories.filter(Files::isDirectory)
                        .filter(path -> !path.equals(root))
                        .filter(path -> !Files.isSymbolicLink(path))
                        .map(this::relativePath)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(result::add);
            }
        } catch (IOException ignored) {
        }
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
        String relative = id == null || id.isBlank() ? uniqueId(source) : normalizeScriptId(id);
        writeScript(relative, source);
        return relative;
    }

    public String createInFolder(String folder, String fileName, String source) {
        String cleanFolder = normalizeFolder(folder);
        String cleanName = normalizeFileName(fileName);
        String relative = cleanFolder.isBlank() ? cleanName : cleanFolder + "/" + cleanName;
        String id = normalizeScriptId(relative);
        int suffix = 2;
        while (Files.exists(pathFor(id))) {
            id = stripExtension(normalizeScriptId(relative)) + " " + suffix++;
        }
        writeScript(id, source);
        return id;
    }

    public String createFolder(String parent, String folderName) {
        String cleanParent = normalizeFolder(parent);
        String cleanName = normalizeFolderSegment(folderName);
        String relative = cleanParent.isBlank() ? cleanName : cleanParent + "/" + cleanName;
        try {
            Files.createDirectories(resolveRelative(relative));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create folder", exception);
        }
        return relative;
    }

    public String importSource(String preferredName, String source) {
        String base = preferredName == null || preferredName.isBlank()
                ? uniqueId(source) : normalizeScriptId(preferredName);
        String id = base;
        int suffix = 2;
        while (Files.exists(pathFor(id))) {
            id = stripExtension(base) + " " + suffix++;
        }
        writeScript(id, source);
        return id;
    }

    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(pathFor(id));
        } catch (IOException exception) {
            return false;
        }
    }

    public Path pathFor(String id) {
        return resolveRelative(normalizeScriptId(id) + ".sace");
    }

    public Path root() {
        return root;
    }

    private void writeScript(String id, String source) {
        Path destination = pathFor(id);
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not save script: " + id, exception);
        }
    }

    private String uniqueId(String source) {
        String title = "script";
        CompilationResult compilation = ScriptCompiler.compile("new", source);
        if (compilation.success()) {
            title = compilation.script().title();
        }
        String base = normalizeScriptId(sanitizeFileName(title));
        String result = base;
        int suffix = 2;
        while (Files.exists(pathFor(result))) {
            result = base + " " + suffix++;
        }
        return result;
    }

    private String normalizeScriptId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid script path");
        }
        String normalized = value.replace('\\', '/').strip();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".sace")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        normalized = normalizeRelativePath(normalized);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Script path cannot be empty");
        }
        return normalized;
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        return normalizeRelativePath(folder.replace('\\', '/').strip());
    }

    private String normalizeRelativePath(String value) {
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] segments = normalized.split("/");
        List<String> valid = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Parent directory traversal is not allowed");
            }
            valid.add(normalizeFolderSegment(segment));
        }
        return String.join("/", valid);
    }

    private String normalizeFolderSegment(String segment) {
        String value = segment == null ? "" : segment.strip();
        if (value.isBlank() || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Invalid folder or file name");
        }
        for (char character : value.toCharArray()) {
            if (character < 32 || INVALID_NAME_CHARACTERS.contains(character)) {
                throw new IllegalArgumentException("Name contains an unsupported character: " + character);
            }
        }
        return value;
    }

    private String normalizeFileName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.toLowerCase(Locale.ROOT).endsWith(".sace")) {
            value = value.substring(0, value.length() - 5);
        }
        return normalizeFolderSegment(value);
    }

    private Path resolveRelative(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the script directory");
        }
        return resolved;
    }

    private String relativePath(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String sanitizeFileName(String value) {
        StringBuilder result = new StringBuilder();
        for (char character : value.toCharArray()) {
            result.append(INVALID_NAME_CHARACTERS.contains(character) || character < 32 ? '_' : character);
        }
        String sanitized = result.toString().strip();
        return sanitized.isBlank() ? "script" : sanitized;
    }

    private static String stripExtension(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".sace")
                ? name.substring(0, name.length() - 5) : name;
    }

    private static String parentOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? "" : relativePath.substring(0, slash);
    }
}
