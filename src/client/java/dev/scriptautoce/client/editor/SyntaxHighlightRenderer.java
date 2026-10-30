package dev.scriptautoce.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;

public final class SyntaxHighlightRenderer {
    private static final int DEFAULT_COLOR = 0xFFD4D4D4;
    private static final int KEYWORD_COLOR = 0xFFC586C0;
    private static final int STRING_COLOR = 0xFFCE9178;
    private static final int NUMBER_COLOR = 0xFFB5CEA8;
    private static final int COMMENT_COLOR = 0xFF6A9955;
    private static final int FUNCTION_COLOR = 0xFFDCDCAA;
    private static final int BOOLEAN_COLOR = 0xFF569CD6;
    private static final Set<String> KEYWORDS = Set.of(
            "meta", "arg", "let", "set", "if", "else", "for", "in",
            "wait", "run", "log", "fail", "return", "true", "false"
    );
    private static final Pattern TOKEN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|#[^\\r\\n]*|\\b\\d+(?:\\.\\d+)?\\b|"
                    + "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()|\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Method BEGIN_INDEX = lineViewMethod("beginIndex");
    private static final Method END_INDEX = lineViewMethod("endIndex");

    private SyntaxHighlightRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, MultiLineEditBox editor,
                              Font font, MultilineTextField textField) {
        String source = textField.value();
        int x = editor.getX() + 4;
        int y = editor.getY() + 4;
        for (Object line : (Iterable<?>) textField.iterateLines()) {
            int begin = (int) invoke(line, BEGIN_INDEX);
            int end = (int) invoke(line, END_INDEX);
            String raw = source.substring(begin, end);
            if (raw.endsWith("\n")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            if (raw.endsWith("\r")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            drawLine(graphics, font, raw, x, y);
            y += 9;
        }
    }

    private static Method lineViewMethod(String name) {
        try {
            Method method = Class.forName("net.minecraft.client.gui.components.MultilineTextField$StringView")
                    .getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Object invoke(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect editor line", exception);
        }
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Font font, String line, int x, int y) {
        if (line.isEmpty()) {
            return;
        }
        Matcher matcher = TOKEN.matcher(line);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                String plain = line.substring(cursor, matcher.start());
                graphics.text(font, plain, x, y, DEFAULT_COLOR, false);
                x += font.width(plain);
            }
            String token = matcher.group();
            int color;
            if (token.startsWith("#")) {
                color = COMMENT_COLOR;
            } else if (token.startsWith("\"")) {
                color = STRING_COLOR;
            } else if (token.matches("\\d+(?:\\.\\d+)?")) {
                color = NUMBER_COLOR;
            } else if (token.equals("true") || token.equals("false")) {
                color = BOOLEAN_COLOR;
            } else if (KEYWORDS.contains(token)) {
                color = KEYWORD_COLOR;
            } else if (matcher.end() < line.length() && line.substring(matcher.end()).stripLeading().startsWith("(")) {
                color = FUNCTION_COLOR;
            } else {
                color = DEFAULT_COLOR;
            }
            graphics.text(font, token, x, y, color, false);
            x += font.width(token);
            cursor = matcher.end();
            if (token.startsWith("#")) {
                return;
            }
        }
        if (cursor < line.length()) {
            graphics.text(font, line.substring(cursor), x, y, DEFAULT_COLOR, false);
        }
    }

    public static List<String> lineNumbers(MultiLineEditBox editor, int visibleLines, double scrollAmount) {
        int lineCount = editor.getValue().split("\\R", -1).length;
        int first = Math.min(lineCount, Math.max(0, (int) (scrollAmount / 9)));
        List<String> result = new ArrayList<>();
        for (int index = first; index < Math.min(lineCount, first + visibleLines); index++) {
            result.add(Integer.toString(index + 1));
        }
        return result;
    }
}
