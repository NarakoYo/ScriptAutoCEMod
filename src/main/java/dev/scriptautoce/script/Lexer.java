package dev.scriptautoce.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Lexer {
    private static final Map<String, Token.TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("meta", Token.TokenType.META),
            Map.entry("arg", Token.TokenType.ARG),
            Map.entry("let", Token.TokenType.LET),
            Map.entry("set", Token.TokenType.SET),
            Map.entry("if", Token.TokenType.IF),
            Map.entry("else", Token.TokenType.ELSE),
            Map.entry("for", Token.TokenType.FOR),
            Map.entry("in", Token.TokenType.IN),
            Map.entry("wait", Token.TokenType.WAIT),
            Map.entry("run", Token.TokenType.RUN),
            Map.entry("log", Token.TokenType.LOG),
            Map.entry("fail", Token.TokenType.FAIL),
            Map.entry("return", Token.TokenType.RETURN),
            Map.entry("true", Token.TokenType.TRUE),
            Map.entry("false", Token.TokenType.FALSE)
    );

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int index;
    private int line = 1;
    private int column = 1;

    private Lexer(String source) {
        this.source = source;
    }

    static List<Token> lex(String source) {
        Lexer lexer = new Lexer(source);
        lexer.scan();
        return lexer.tokens;
    }

    private void scan() {
        while (!atEnd()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
                continue;
            }
            if (c == '#') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
                continue;
            }

            int startLine = line;
            int startColumn = column;
            if (Character.isLetter(c) || c == '_') {
                identifier(startLine, startColumn);
            } else if (Character.isDigit(c)) {
                number(startLine, startColumn);
            } else {
                switch (c) {
                    case '"' -> string(startLine, startColumn);
                    case '(' -> single(Token.TokenType.LPAREN, startLine, startColumn);
                    case ')' -> single(Token.TokenType.RPAREN, startLine, startColumn);
                    case '{' -> single(Token.TokenType.LBRACE, startLine, startColumn);
                    case '}' -> single(Token.TokenType.RBRACE, startLine, startColumn);
                    case ',' -> single(Token.TokenType.COMMA, startLine, startColumn);
                    case ':' -> single(Token.TokenType.COLON, startLine, startColumn);
                    case ';' -> single(Token.TokenType.SEMICOLON, startLine, startColumn);
                    case '+' -> single(Token.TokenType.PLUS, startLine, startColumn);
                    case '-' -> single(Token.TokenType.MINUS, startLine, startColumn);
                    case '*' -> single(Token.TokenType.STAR, startLine, startColumn);
                    case '%' -> single(Token.TokenType.PERCENT, startLine, startColumn);
                    case '/' -> single(Token.TokenType.SLASH, startLine, startColumn);
                    case '=' -> {
                        advance();
                        add(match('=') ? Token.TokenType.EQUAL_EQUAL : Token.TokenType.EQUAL, "=", startLine, startColumn);
                    }
                    case '!' -> {
                        advance();
                        add(match('=') ? Token.TokenType.BANG_EQUAL : Token.TokenType.BANG, "!", startLine, startColumn);
                    }
                    case '<' -> {
                        advance();
                        add(match('=') ? Token.TokenType.LESS_EQUAL : Token.TokenType.LESS, "<", startLine, startColumn);
                    }
                    case '>' -> {
                        advance();
                        add(match('=') ? Token.TokenType.GREATER_EQUAL : Token.TokenType.GREATER, ">", startLine, startColumn);
                    }
                    case '&' -> {
                        advance();
                        if (!match('&')) {
                            throw error(startLine, startColumn, "Expected &&");
                        }
                        add(Token.TokenType.AND_AND, "&&", startLine, startColumn);
                    }
                    case '|' -> {
                        advance();
                        if (!match('|')) {
                            throw error(startLine, startColumn, "Expected ||");
                        }
                        add(Token.TokenType.OR_OR, "||", startLine, startColumn);
                    }
                    default -> throw error(startLine, startColumn, "Unexpected character '" + c + "'");
                }
            }
        }
        tokens.add(new Token(Token.TokenType.EOF, "", line, column));
    }

    private void identifier(int startLine, int startColumn) {
        StringBuilder text = new StringBuilder();
        while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            text.append(advance());
        }
        String value = text.toString();
        Token.TokenType type = KEYWORDS.getOrDefault(value, Token.TokenType.IDENTIFIER);
        tokens.add(new Token(type, value, startLine, startColumn));
    }

    private void number(int startLine, int startColumn) {
        int start = index;
        while (!atEnd() && Character.isDigit(peek())) {
            advance();
        }
        boolean decimal = false;
        if (!atEnd() && peek() == '.' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1))) {
            decimal = true;
            advance();
            while (!atEnd() && Character.isDigit(peek())) {
                advance();
            }
        }
        tokens.add(new Token(decimal ? Token.TokenType.DECIMAL : Token.TokenType.INTEGER,
                source.substring(start, index), startLine, startColumn));
    }

    private void string(int startLine, int startColumn) {
        advance();
        StringBuilder value = new StringBuilder();
        while (!atEnd() && peek() != '"') {
            char c = advance();
            if (c == '\\') {
                if (atEnd()) {
                    throw error(startLine, startColumn, "Unterminated string");
                }
                char escaped = advance();
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> throw error(line, column, "Unknown escape sequence \\" + escaped);
                });
            } else if (c == '$' && !atEnd() && peek() == '{') {
                value.append(c).append(advance());
                int depth = 1;
                while (!atEnd() && depth > 0) {
                    char nested = advance();
                    value.append(nested);
                    if (nested == '\\' && !atEnd()) {
                        value.append(advance());
                    } else if (nested == '{') {
                        depth++;
                    } else if (nested == '}') {
                        depth--;
                    }
                }
                if (depth != 0) {
                    throw error(startLine, startColumn, "Unclosed interpolation");
                }
            } else {
                value.append(c);
            }
        }
        if (atEnd()) {
            throw error(startLine, startColumn, "Unterminated string");
        }
        advance();
        tokens.add(new Token(Token.TokenType.STRING, value.toString(), startLine, startColumn));
    }

    private void single(Token.TokenType type, int startLine, int startColumn) {
        char c = advance();
        add(type, Character.toString(c), startLine, startColumn);
    }

    private void add(Token.TokenType type, String text, int startLine, int startColumn) {
        tokens.add(new Token(type, text, startLine, startColumn));
    }

    private boolean match(char expected) {
        if (atEnd() || peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char advance() {
        char c = source.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private char peek() {
        return source.charAt(index);
    }

    private boolean atEnd() {
        return index >= source.length();
    }

    private static ScriptException error(int line, int column, String message) {
        return new ScriptException(line, column, message);
    }
}
