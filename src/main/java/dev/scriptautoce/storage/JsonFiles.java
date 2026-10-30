package dev.scriptautoce.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonFiles() {
    }

    public static <T> T read(Path path, Class<T> type, T fallback) {
        if (!Files.isRegularFile(path)) {
            return fallback;
        }
        try {
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException | JsonParseException exception) {
            return fallback;
        }
    }

    public static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write " + path, exception);
        }
    }
}
