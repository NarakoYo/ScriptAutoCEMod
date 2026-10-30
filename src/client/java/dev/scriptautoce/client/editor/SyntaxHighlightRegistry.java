package dev.scriptautoce.client.editor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.MultiLineEditBox;

public final class SyntaxHighlightRegistry {
    private static final Map<MultiLineEditBox, Boolean> ENABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SyntaxHighlightRegistry() {
    }

    public static void register(MultiLineEditBox editor) {
        ENABLED.put(editor, Boolean.TRUE);
    }

    public static void unregister(MultiLineEditBox editor) {
        ENABLED.remove(editor);
    }

    public static boolean isEnabled(MultiLineEditBox editor) {
        return ENABLED.containsKey(editor);
    }
}
