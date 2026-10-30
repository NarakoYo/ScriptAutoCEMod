package dev.scriptautoce.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditLog {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private AuditLog() {
    }

    public static synchronized void write(String type, Map<String, ?> fields) {
        try {
            Files.createDirectories(ConfigPaths.auditLogs());
            Path path = ConfigPaths.auditLogs().resolve("audit-" + DAY.format(Instant.now()) + ".jsonl");
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("time", Instant.now().toString());
            record.put("type", type);
            record.putAll(fields);
            Files.writeString(path, JsonFiles.COMPACT_GSON.toJson(record) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Audit failures must not break gameplay.
        }
    }
}
