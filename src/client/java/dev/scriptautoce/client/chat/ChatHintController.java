package dev.scriptautoce.client.chat;

import dev.scriptautoce.client.ui.ClientUiState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ChatHintController {
    private static final List<Hint> BASE_COMMANDS = List.of(
            hint("##list"),
            hint("##run "),
            hint("##stop "),
            hint("##status"),
            hint("##share "),
            hint("##download "),
            hint("##help")
    );

    private ChatHintController() {
    }

    public static List<Hint> hints(String input) {
        if (input == null || !input.startsWith("##")) {
            return List.of();
        }
        if (!input.contains(" ")) {
            return BASE_COMMANDS.stream()
                    .filter(hint -> hint.completion().startsWith(input))
                    .toList();
        }

        LinkedHashMap<String, Hint> result = new LinkedHashMap<>();
        if (input.startsWith("##run ")) {
            String query = input.substring(6).toLowerCase(Locale.ROOT);
            ClientUiState.localScripts().forEach(script -> add(result,
                    completion("##run ", script.id()), query));
            ClientUiState.sharedScripts().forEach(script -> add(result,
                    completion("##run ", script.id()), query));
        } else if (input.startsWith("##share ")) {
            String query = input.substring(8).toLowerCase(Locale.ROOT);
            ClientUiState.localScripts().forEach(script -> add(result,
                    completion("##share ", script.id()), query));
        } else if (input.startsWith("##download ")) {
            String query = input.substring(11).toLowerCase(Locale.ROOT);
            ClientUiState.sharedScripts().forEach(script -> add(result,
                    completion("##download ", script.id()), query));
        } else if (input.startsWith("##stop ")) {
            String query = input.substring(7).toLowerCase(Locale.ROOT);
            ClientUiState.jobs().forEach((id, job) -> add(result, "##stop " + id, query));
        }
        return List.copyOf(result.values());
    }

    public static String firstCompletion(String input) {
        List<Hint> hints = hints(input);
        return hints.isEmpty() ? null : hints.getFirst().completion();
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, String input,
                              int x, int y, int inputWidth) {
        List<Hint> hints = hints(input);
        if (hints.isEmpty()) {
            return;
        }
        int visible = Math.min(8, hints.size());
        int rowHeight = 10;
        int top = Math.max(2, y - visible * rowHeight - 7);
        int width = 0;
        for (int index = 0; index < visible; index++) {
            width = Math.max(width, font.width(hints.get(index).completion()));
        }
        width = Math.min(inputWidth - 4, width + 10);
        graphics.nextStratum();
        graphics.fill(x - 2, top - 2, x + width + 4, top + visible * rowHeight + 2, 0xC0101010);
        for (int index = 0; index < visible; index++) {
            String completion = hints.get(index).completion().stripTrailing();
            int space = completion.indexOf(' ');
            String command = space < 0 ? completion : completion.substring(0, space);
            graphics.text(font, command, x + 2, top + index * rowHeight, 0xFF55FFFF, false);
            if (space >= 0) {
                graphics.text(font, completion.substring(space), x + 2 + font.width(command),
                        top + index * rowHeight, 0xFFAAAAAA, false);
            }
        }
    }

    private static void add(LinkedHashMap<String, Hint> hints, String completion, String query) {
        if (completion.toLowerCase(Locale.ROOT).contains(query)) {
            hints.putIfAbsent(completion, hint(completion));
        }
    }

    private static String completion(String command, String value) {
        return command + (value.chars().anyMatch(Character::isWhitespace)
                ? "\"" + value.replace("\"", "\\\"") + "\"" : value);
    }

    private static Hint hint(String completion) {
        return new Hint(completion);
    }

    public record Hint(String completion) {
    }
}
