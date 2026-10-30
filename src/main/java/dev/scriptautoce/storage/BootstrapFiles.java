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
            Files.createDirectories(ConfigPaths.gameRoot().resolve("Examples"));
            if (!Files.exists(ConfigPaths.clientSettings())) {
                dev.scriptautoce.config.ClientSettings.defaults().save();
            }
            if (!Files.exists(ConfigPaths.serverSettings())) {
                dev.scriptautoce.config.ServerSettings.defaults().save();
            }
            migrateOldTree(ConfigPaths.client().resolve("scripts"), ConfigPaths.localScripts());
            migrateOldTree(ConfigPaths.server().resolve("shared"), ConfigPaths.sharedScripts());
            Path oldSharedIndex = ConfigPaths.server().resolve("shared-index.json");
            if (Files.isRegularFile(oldSharedIndex) && !Files.exists(ConfigPaths.sharedIndex())) {
                Files.createDirectories(ConfigPaths.sharedIndex().getParent());
                Files.copy(oldSharedIndex, ConfigPaths.sharedIndex(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            copyResourceIfMissing("examples/welcome.sace",
                    ConfigPaths.localScripts().resolve("Examples/welcome.sace"));
            copyResourceIfMissing("examples/spawn_10_fake_players.sace",
                    ConfigPaths.localScripts().resolve("示例/生成10个假人.sace"));
            copyResourceIfMissing("examples/welcome.sace",
                    ConfigPaths.gameRoot().resolve("Examples/welcome.sace"));
            copyResourceIfMissing("examples/spawn_10_fake_players.sace",
                    ConfigPaths.gameRoot().resolve("Examples/生成10个假人.sace"));
            copyResourceIfMissing("docs/language.md", ConfigPaths.gameRoot().resolve("LANGUAGE.md"));
            copyResourceIfMissing("docs/language.md", ConfigPaths.gameRoot().resolve("语言文档.md"));
            copyResourceIfMissing("docs/SACE_SCRIPT_TUTORIAL.html",
                    ConfigPaths.gameRoot().resolve("SACE脚本教程.html"));
            copyResourceIfMissing("docs/SACE_SCRIPT_TUTORIAL.md",
                    ConfigPaths.gameRoot().resolve("SACE脚本教程.md"));
            copyResourceIfMissing("docs/language.md", ConfigPaths.gameRoot().resolve("README.md"));
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

    private static void migrateOldTree(Path sourceRoot, Path destinationRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }
        try (var files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path destination = destinationRoot.resolve(sourceRoot.relativize(source));
                    if (!Files.exists(destination)) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException ignored) {
                }
            });
        }
    }
}
