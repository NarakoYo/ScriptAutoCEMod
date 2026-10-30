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
    public static final int LINE_HEIGHT = 9;
    private static final int DEFAULT_COLOR = 0xFFD4D4D4;
    private static final int KEYWORD_COLOR = 0xFFC586C0;
    private static final int STRING_COLOR = 0xFFCE9178;
    private static final int NUMBER_COLOR = 0xFFB5CEA8;
    private static final int COMMENT_COLOR = 0xFF6A9955;
    private static final int FUNCTION_COLOR = 0xFFDCDCAA;
    private static final int TYPE_COLOR = 0xFF4EC9B0;
    private static final int BOOLEAN_COLOR = 0xFF569CD6;
    private static final Set<String> KEYWORDS = Set.of(
            "meta", "arg", "let", "set", "if", "else", "for", "in",
            "wait", "run", "log", "fail", "return"
    );
    private static final Set<String> TYPES = Set.of(
            "int", "double", "bool", "string", "token", "selector", "vec3"
    );
    private static final Set<String> FUNCTIONS = Set.of(
            "pos", "block", "players", "entities", "score", "token", "quote", "json",
            "min", "max", "abs", "round", "floor", "ceil", "clamp",
            "length", "contains", "startsWith", "endsWith", "lower", "upper",
            "trim", "replace", "join", "range"
    );
    private static final Pattern TOKEN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|#[^\\r\\n]*|\\b\\d+(?:\\.\\d+)?\\b|"
                    + "\\b[A-Za-z_][A-Za-z0-9_]*\\b|===?|!==?|<=|>=|&&|\\|\\||[-+*/%]");
    private static final Method BEGIN_INDEX = lineViewMethod("beginIndex");
    private static final Method END_INDEX = lineViewMethod("endIndex");

    private SyntaxHighlightRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, MultiLineEditBox editor,
                              Font font, MultilineTextField textField) {
        String source = textField.value();
        if (source.isEmpty()) {
            return;
        }
        double scroll = editor.scrollAmount();
        int firstLine = Math.max(0, (int) Math.floor(scroll / LINE_HEIGHT));
        int fractional = (int) Math.round(scroll - firstLine * LINE_HEIGHT);
        int x = editor.getX() + 4;
        int top = editor.getY() + 4;
        int bottom = editor.getY() + editor.getInnerHeight() - 2;
        int lineCount = textField.getLineCount();

        graphics.nextStratum();
        for (int lineIndex = firstLine; lineIndex < lineCount; lineIndex++) {
            int y = top + (lineIndex - firstLine) * LINE_HEIGHT - fractional;
            if (y + LINE_HEIGHT < top) {
                continue;
            }
            if (y > bottom) {
                break;
            }
            Object view = textField.getLineView(lineIndex);
            int begin = Math.max(0, Math.min(source.length(), invokeLineIndex(view, BEGIN_INDEX)));
            int end = Math.max(begin, Math.min(source.length(), invokeLineIndex(view, END_INDEX)));
            String raw = stripLineEnding(source.substring(begin, end));
            drawLine(graphics, font, raw, x, y);
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

    private static int invokeLineIndex(Object target, Method method) {
        try {
            return (int) method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect editor line", exception);
        }
    }

    private static String stripLineEnding(String value) {
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n") || value.endsWith("\r")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Font font, String line, int x, int y) {
        if (line.isEmpty()) {
            return;
        }
        Matcher matcher = TOKEN.matcher(line);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                x = drawToken(graphics, font, line.substring(cursor, matcher.start()),
                        DEFAULT_COLOR, x, y);
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
            } else if (TYPES.contains(token)) {
                color = TYPE_COLOR;
            } else if (KEYWORDS.contains(token)) {
                color = KEYWORD_COLOR;
            } else if (FUNCTIONS.contains(token)) {
                color = FUNCTION_COLOR;
            } else {
                color = DEFAULT_COLOR;
            }
            x = drawToken(graphics, font, token, color, x, y);
            cursor = matcher.end();
            if (token.startsWith("#")) {
                return;
            }
        }
        if (cursor < line.length()) {
            drawToken(graphics, font, line.substring(cursor), DEFAULT_COLOR, x, y);
        }
    }

    private static int drawToken(GuiGraphicsExtractor graphics, Font font, String text,
                                 int color, int x, int y) {
        if (text.isEmpty()) {
            return x;
        }
        graphics.text(font, text, x, y, color, false);
        return x + font.width(text);
    }

    public static List<String> lineNumbers(MultiLineEditBox editor, int visibleLines,
                                           double scrollAmount) {
        int lineCount = editor.getValue().split("\\R", -1).length;
        int first = Math.min(lineCount, Math.max(0,
                (int) Math.floor(scrollAmount / LINE_HEIGHT)));
        List<String> result = new ArrayList<>();
        for (int index = first; index < Math.min(lineCount, first + visibleLines); index++) {
            result.add(Integer.toString(index + 1));
        }
        return result;
    }

    public static int lineAt(MultiLineEditBox editor, double mouseY) {
        double scroll = editor.scrollAmount();
        int first = Math.max(0, (int) Math.floor(scroll / LINE_HEIGHT));
        int offset = (int) Math.round(scroll - first * LINE_HEIGHT);
        int line = first + (int) Math.floor((mouseY - editor.getY() - 4 + offset) / LINE_HEIGHT);
        int lineCount = editor.getValue().split("\\R", -1).length;
        return Math.max(0, Math.min(lineCount - 1, line));
    }

    public static int firstVisibleLine(MultiLineEditBox editor) {
        return Math.max(0, (int) Math.floor(editor.scrollAmount() / LINE_HEIGHT));
    }

    public static int lineY(MultiLineEditBox editor, int zeroBasedLine) {
        int first = firstVisibleLine(editor);
        int fractional = (int) Math.round(editor.scrollAmount() - first * LINE_HEIGHT);
        return editor.getY() + 4 + (zeroBasedLine - first) * LINE_HEIGHT - fractional;
    }
}
