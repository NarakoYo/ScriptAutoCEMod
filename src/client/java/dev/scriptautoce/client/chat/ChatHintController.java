package dev.scriptautoce.client.chat;

import dev.scriptautoce.client.ui.ClientUiState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
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
    private static final Map<Object, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int ROW_HEIGHT = 10;

    private ChatHintController() {
    }

    public static String cycleCompletion(Object owner, String input, boolean backwards) {
        State state = STATES.computeIfAbsent(owner, ignored -> new State());
        if (!input.equals(state.lastCompletion)) {
            state.baseInput = input;
            state.selectedIndex = -1;
        }
        List<Hint> hints = hints(state.baseInput);
        if (hints.isEmpty()) {
            return null;
        }
        int direction = backwards ? -1 : 1;
        state.selectedIndex = Math.floorMod(state.selectedIndex + direction, hints.size());
        state.hints = hints;
        state.lastCompletion = hints.get(state.selectedIndex).completion();
        return state.lastCompletion;
    }

    public static String mouseClicked(Object owner, String input, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return null;
        }
        State state = STATES.get(owner);
        if (state == null || state.hits == null || state.hints.isEmpty()) {
            return null;
        }
        int row = state.hits.rowAt(mouseX, mouseY);
        if (row < 0 || row >= state.hints.size()) {
            return null;
        }
        if (!input.equals(state.lastCompletion)) {
            state.baseInput = input;
        }
        state.selectedIndex = row;
        state.lastCompletion = state.hints.get(row).completion();
        return state.lastCompletion;
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

    public static void render(Object owner, GuiGraphicsExtractor graphics, Font font, String input,
                              int x, int y, int inputWidth, int mouseX, int mouseY) {
        State state = STATES.computeIfAbsent(owner, ignored -> new State());
        List<Hint> hints = displayedHints(state, input);
        if (hints.isEmpty()) {
            state.hits = null;
            state.hints = List.of();
            return;
        }
        int visible = Math.min(8, hints.size());
        int top = Math.max(2, y - visible * ROW_HEIGHT - 7);
        int width = 0;
        for (int index = 0; index < visible; index++) {
            width = Math.max(width, font.width(hints.get(index).completion()));
        }
        width = Math.min(inputWidth - 4, width + 10);
        HitBox hits = new HitBox(x - 2, top - 2, x + width + 4, top + visible * ROW_HEIGHT + 2);
        state.hits = hits;
        state.hints = hints;
        int hovered = hits.rowAt(mouseX, mouseY);

        graphics.nextStratum();
        graphics.fill(hits.left, hits.top, hits.right, hits.bottom, 0xC0101010);
        for (int index = 0; index < visible; index++) {
            int rowY = top + index * ROW_HEIGHT;
            if (index == hovered || index == state.selectedIndex) {
                graphics.fill(hits.left + 1, rowY - 1, hits.right - 1, rowY + ROW_HEIGHT - 1, 0x8060A5FA);
            }
            String completion = hints.get(index).completion().stripTrailing();
            int space = completion.indexOf(' ');
            String command = space < 0 ? completion : completion.substring(0, space);
            graphics.text(font, command, x + 2, rowY, 0xFF55FFFF, false);
            if (space >= 0) {
                graphics.text(font, completion.substring(space), x + 2 + font.width(command),
                        rowY, 0xFFAAAAAA, false);
            }
        }
    }

    private static List<Hint> displayedHints(State state, String input) {
        if (state.lastCompletion != null && input.equals(state.lastCompletion)
                && !state.hints.isEmpty()) {
            return state.hints;
        }
        state.selectedIndex = -1;
        state.lastCompletion = null;
        return hints(input);
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

    private static final class State {
        private String baseInput = "";
        private String lastCompletion;
        private int selectedIndex = -1;
        private List<Hint> hints = List.of();
        private HitBox hits;
    }

    private record HitBox(int left, int top, int right, int bottom) {
        private int rowAt(double mouseX, double mouseY) {
            if (mouseX < left || mouseX >= right || mouseY < top + 2 || mouseY >= bottom - 2) {
                return -1;
            }
            return (int) ((mouseY - top - 2) / ROW_HEIGHT);
        }
    }
}
