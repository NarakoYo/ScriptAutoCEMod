package dev.scriptautoce.storage;

import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.policy.CommandPolicy;
import dev.scriptautoce.policy.PolicyPreflight;
import dev.scriptautoce.script.CompilationResult;
import dev.scriptautoce.script.ScriptCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SharedScriptStore {
    public record Info(
            String id,
            String title,
            String ownerUuid,
            String ownerName,
            int revision,
            String hash,
            String updatedAt,
            long size
    ) {
    }

    public record Index(int schemaVersion, List<Info> scripts) {
        public Index {
            scripts = scripts == null ? List.of() : List.copyOf(scripts);
        }

        public static Index empty() {
            return new Index(1, List.of());
        }
    }

    public Info upload(UUID ownerUuid, String ownerName, String requestedId, String source,
                       ServerSettings settings) {
        if (!settings.sharingEnabled()) {
            throw new IllegalArgumentException("Script sharing is disabled");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > settings.maxSourceBytes()) {
            throw new IllegalArgumentException("Script exceeds the server source limit");
        }
        CompilationResult compilation = ScriptCompiler.compile(requestedId == null ? "shared" : requestedId, source);
        if (!compilation.success()) {
            String message = compilation.diagnostics().isEmpty()
                    ? "Script did not compile" : compilation.diagnostics().getFirst().toString();
            throw new IllegalArgumentException(message);
        }
        CommandPolicy policy = settings.policy();
        List<String> violations = PolicyPreflight.findStaticViolations(compilation.script(), policy);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.getFirst());
        }

        Index index = loadIndex();
        List<Info> scripts = new ArrayList<>(index.scripts());
        Info existing = requestedId == null ? null : scripts.stream()
                .filter(info -> info.id().equals(requestedId)).findFirst().orElse(null);
        if (existing != null && !existing.ownerUuid().equals(ownerUuid.toString())) {
            throw new IllegalArgumentException("Only the owner can update this shared script");
        }
        if (existing == null) {
            long owned = scripts.stream().filter(info -> info.ownerUuid().equals(ownerUuid.toString())).count();
            if (owned >= settings.maxUploadsPerPlayer()) {
                throw new IllegalArgumentException("Shared script count quota reached");
            }
        }
        long ownedBytes = scripts.stream()
                .filter(info -> info.ownerUuid().equals(ownerUuid.toString()))
                .filter(info -> existing == null || !info.id().equals(existing.id()))
                .mapToLong(Info::size)
                .sum();
        if (ownedBytes + source.getBytes(StandardCharsets.UTF_8).length > settings.maxSharedBytesPerPlayer()) {
            throw new IllegalArgumentException("Shared script byte quota reached");
        }

        String id = existing == null ? UUID.randomUUID().toString() : existing.id();
        int revision = existing == null ? 1 : existing.revision() + 1;
        String hash = ScriptCompiler.sha256(source);
        String title = sanitizeTitle(compilation.script().title());
        Info info = new Info(id, title, ownerUuid.toString(), ownerName, revision, hash,
                Instant.now().toString(), source.getBytes(StandardCharsets.UTF_8).length);
        scripts.removeIf(candidate -> candidate.id().equals(id));
        scripts.add(info);
        scripts.sort(Comparator.comparing(Info::updatedAt).reversed());
        writeSource(id, source);
        JsonFiles.write(ConfigPaths.sharedIndex(), new Index(1, scripts));
        AuditLog.write("share-upload", java.util.Map.of(
                "script", id, "owner", ownerUuid, "revision", revision, "hash", hash));
        return info;
    }

    public String download(String id) {
        Info info = find(id);
        if (info == null) {
            throw new IllegalArgumentException("Shared script not found");
        }
        try {
            return Files.readString(sourcePath(id), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Shared script file is missing", exception);
        }
    }

    public List<Info> list(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return loadIndex().scripts().stream()
                .filter(info -> normalized.isBlank()
                        || info.title().toLowerCase(Locale.ROOT).contains(normalized)
                        || info.ownerName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    public Info find(String id) {
        if (id == null) {
            return null;
        }
        return loadIndex().scripts().stream().filter(info -> info.id().equals(id)).findFirst().orElse(null);
    }

    public boolean delete(String id, UUID requester, boolean admin) {
        Info info = find(id);
        if (info == null) {
            return false;
        }
        if (!admin && !info.ownerUuid().equals(requester.toString())) {
            throw new IllegalArgumentException("Only the owner or an administrator can delete this script");
        }
        List<Info> scripts = new ArrayList<>(loadIndex().scripts());
        scripts.removeIf(candidate -> candidate.id().equals(id));
        JsonFiles.write(ConfigPaths.sharedIndex(), new Index(1, scripts));
        try {
            Files.deleteIfExists(sourcePath(id));
        } catch (IOException ignored) {
        }
        AuditLog.write("share-delete", java.util.Map.of("script", id, "requester", requester, "admin", admin));
        return true;
    }

    private Index loadIndex() {
        Index index = JsonFiles.read(ConfigPaths.sharedIndex(), Index.class, Index.empty());
        return index == null ? Index.empty() : index;
    }

    private void writeSource(String id, String source) {
        validateId(id);
        try {
            Files.createDirectories(ConfigPaths.sharedScripts());
            Path destination = sourcePath(id);
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store shared script", exception);
        }
    }

    private Path sourcePath(String id) {
        validateId(id);
        return ConfigPaths.sharedScripts().resolve(id + ".sace");
    }

    private static void validateId(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid shared script id");
        }
    }

    private static String sanitizeTitle(String title) {
        String value = title == null ? "untitled" : title.replaceAll("[\\r\\n\\t]", " ").strip();
        return value.isBlank() ? "untitled" : value.substring(0, Math.min(80, value.length()));
    }
}
