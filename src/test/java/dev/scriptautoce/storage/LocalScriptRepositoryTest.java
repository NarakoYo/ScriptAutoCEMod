package dev.scriptautoce.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalScriptRepositoryTest {
    @TempDir
    Path temporary;

    @Test
    void supportsNestedUnicodeFoldersAndFileNames() {
        LocalScriptRepository repository = new LocalScriptRepository(temporary);

        String id = repository.createInFolder("示例/自动化", "欢迎脚本",
                "meta name \"欢迎\"\nlog \"你好\"\n");

        assertEquals("示例/自动化/欢迎脚本", id);
        assertEquals(1, repository.list().size());
        assertEquals("示例/自动化/欢迎脚本", repository.list().getFirst().id());
        assertTrue(repository.folders().contains("示例/自动化"));
        assertEquals("meta name \"欢迎\"\nlog \"你好\"\n", repository.load(id));
    }

    @Test
    void rejectsParentTraversal() {
        LocalScriptRepository repository = new LocalScriptRepository(temporary);

        assertThrows(IllegalArgumentException.class,
                () -> repository.save("../outside", "meta name \"escape\""));
    }

    @Test
    void createsFoldersInsideSelectedDirectory() {
        LocalScriptRepository repository = new LocalScriptRepository(temporary);

        String folder = repository.createFolder("中文", "子目录");

        assertEquals("中文/子目录", folder);
        assertTrue(repository.folders().contains(folder));
    }
}
