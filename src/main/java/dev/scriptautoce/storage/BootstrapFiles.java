package dev.scriptautoce.storage;

import dev.scriptautoce.ScriptAutoCe;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BootstrapFiles {
    private BootstrapFiles() {
    }

    public static void ensure() {
        try {
            Files.createDirectories(ConfigPaths.localScripts());
            Files.createDirectories(ConfigPaths.downloadCache());
            Files.createDirectories(ConfigPaths.server());
            Files.createDirectories(ConfigPaths.sharedScripts());
            Files.createDirectories(ConfigPaths.auditLogs());
            if (!Files.exists(ConfigPaths.clientSettings())) {
                dev.scriptautoce.config.ClientSettings.defaults().save();
            }
            if (!Files.exists(ConfigPaths.serverSettings())) {
                dev.scriptautoce.config.ServerSettings.defaults().save();
            }
            copyResourceIfMissing("examples/welcome.sace", ConfigPaths.localScripts().resolve("welcome.sace"));
            copyResourceIfMissing("examples/spawn_10_fake_players.sace",
                    ConfigPaths.localScripts().resolve("spawn_10_fake_players.sace"));
            copyResourceIfMissing("examples/welcome.sace", ConfigPaths.server().resolve("examples/welcome.sace"));
            copyResourceIfMissing("examples/spawn_10_fake_players.sace",
                    ConfigPaths.server().resolve("examples/spawn_10_fake_players.sace"));
            copyResourceIfMissing("docs/language.md", ConfigPaths.root().resolve("language.md"));
        } catch (IOException exception) {
            ScriptAutoCe.LOGGER.error("Could not create Script Auto CE directories", exception);
        }
    }

    private static void copyResourceIfMissing(String resource, Path destination) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream input = BootstrapFiles.class.getClassLoader()
                .getResourceAsStream("assets/scriptautoce/" + resource)) {
            if (input == null) {
                ScriptAutoCe.LOGGER.warn("Bundled resource {} is missing", resource);
                return;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
