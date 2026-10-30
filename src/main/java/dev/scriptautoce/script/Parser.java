package dev.scriptautoce.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Parser {
    private final List<Token> tokens;
    private int current;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static ScriptAst parse(String source) {
        return new Parser(Lexer.lex(source)).program();
    }

    static Expr parseStandaloneExpression(String source, int line, int column) {
        List<Token> expressionTokens = new ArrayList<>(Lexer.lex(source));
        if (!expressionTokens.isEmpty()) {
            Token eof = expressionTokens.removeLast();
            if (eof.line() == 1 && eof.column() == 1 && eof.type() == Token.TokenType.EOF) {
                // Keep the EOF token.
            }
            expressionTokens.add(new Token(Token.TokenType.EOF, "", line, column));
        }
        Parser parser = new Parser(expressionTokens);
        Expr expression = parser.expression();
        parser.expect(Token.TokenType.EOF, "Unexpected text after expression");
        return expression;
    }

    private ScriptAst program() {
        Map<String, ScriptAst.MetaValue> metadata = new LinkedHashMap<>();
        List<ScriptAst.Argument> arguments = new ArrayList<>();
        List<Stmt> statements = new ArrayList<>();
        while (!check(Token.TokenType.EOF)) {
            if (match(Token.TokenType.META)) {
                Token key = expect(Token.TokenType.IDENTIFIER, "Expected metadata name");
                Template value = templateExpression("Expected metadata value");
                metadata.put(key.text(), new ScriptAst.MetaValue(value, key.line()));
            } else if (match(Token.TokenType.ARG)) {
                arguments.add(argument());
            } else {
                statements.add(statement());
            }
            match(Token.TokenType.SEMICOLON);
        }
        return new ScriptAst(metadata, arguments, statements);
    }

    private ScriptAst.Argument argument() {
        Token name = expect(Token.TokenType.IDENTIFIER, "Expected argument name");
        expect(Token.TokenType.COLON, "Expected ':' after argument name");
        Token type = expect(Token.TokenType.IDENTIFIER, "Expected argument type");
        expect(Token.TokenType.EQUAL, "Expected '=' before argument default");
        Expr defaultValue = expression();
        Expr min = null;
        Expr max = null;
        if (matchIdentifier("min")) {
            min = expression();
        }
        if (matchIdentifier("max")) {
            max = expression();
        }
        return new ScriptAst.Argument(name.text(), type.text(), defaultValue, min, max, name.line());
    }

    private Stmt statement() {
        if (match(Token.TokenType.LET)) {
            Token name = expect(Token.TokenType.IDENTIFIER, "Expected variable name");
            expect(Token.TokenType.EQUAL, "Expected '=' after variable name");
            return new Stmt.Let(name.text(), expression(), name.line());
        }
        if (match(Token.TokenType.SET)) {
            Token name = expect(Token.TokenType.IDENTIFIER, "Expected variable name");
            expect(Token.TokenType.EQUAL, "Expected '=' after variable name");
            return new Stmt.Set(name.text(), expression(), name.line());
        }
        if (match(Token.TokenType.IF)) {
            return ifStatement();
        }
        if (match(Token.TokenType.FOR)) {
            return forStatement();
        }
        if (match(Token.TokenType.WAIT)) {
            Token amount = expect(Token.TokenType.INTEGER, "Expected a positive tick count");
            int ticks;
            try {
                ticks = Integer.parseInt(amount.text());
            } catch (NumberFormatException exception) {
                throw error(amount, "Tick count is too large");
            }
            if (ticks < 1 || ticks > 72_000) {
                throw error(amount, "Tick count must be between 1 and 72000");
            }
            matchIdentifier("t");
            return new Stmt.Wait(ticks, amount.line());
        }
        if (match(Token.TokenType.RUN)) {
            return new Stmt.Run(templateExpression("Expected a command string after run"), previous().line());
        }
        if (match(Token.TokenType.LOG)) {
            return new Stmt.Log(templateExpression("Expected a message after log"), previous().line());
        }
        if (match(Token.TokenType.FAIL)) {
            return new Stmt.Fail(templateExpression("Expected a message after fail"), previous().line());
        }
        if (match(Token.TokenType.RETURN)) {
            return new Stmt.Return(previous().line());
        }
        throw error(peek(), "Expected a statement");
    }

    private Stmt ifStatement() {
        int line = previous().line();
        Expr condition = expression();
        List<Stmt> thenBranch = block();
        List<Stmt> elseBranch = List.of();
        if (match(Token.TokenType.ELSE)) {
            if (match(Token.TokenType.IF)) {
                elseBranch = List.of(ifStatement());
            } else {
                elseBranch = block();
            }
        }
        return new Stmt.If(condition, thenBranch, elseBranch, line);
    }

    private Stmt forStatement() {
        int line = previous().line();
        Token variable = expect(Token.TokenType.IDENTIFIER, "Expected loop variable");
        expect(Token.TokenType.IN, "Expected 'in'");
        Token range = expect(Token.TokenType.IDENTIFIER, "Expected range()");
        if (!range.text().equals("range")) {
            throw error(range, "Only range() is supported in for loops");
        }
        expect(Token.TokenType.LPAREN, "Expected '('");
        Expr start = expression();
        expect(Token.TokenType.COMMA, "Expected ','");
        Expr end = expression();
        Expr step = null;
        if (match(Token.TokenType.COMMA)) {
            step = expression();
        }
        expect(Token.TokenType.RPAREN, "Expected ')'");
        return new Stmt.For(variable.text(), start, end, step, block(), line);
    }

    private List<Stmt> block() {
        expect(Token.TokenType.LBRACE, "Expected '{'");
        List<Stmt> statements = new ArrayList<>();
        while (!check(Token.TokenType.RBRACE) && !check(Token.TokenType.EOF)) {
            statements.add(statement());
            match(Token.TokenType.SEMICOLON);
        }
        expect(Token.TokenType.RBRACE, "Expected '}'");
        return statements;
    }

    private Template templateExpression(String message) {
        Token token = expect(Token.TokenType.STRING, message);
        return Template.parse(token.text(), token.line(), token.column());
    }

    private Expr expression() {
        return or();
    }

    private Expr or() {
        Expr expression = and();
        while (match(Token.TokenType.OR_OR)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), and(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr and() {
        Expr expression = equality();
        while (match(Token.TokenType.AND_AND)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), equality(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr equality() {
        Expr expression = comparison();
        while (match(Token.TokenType.EQUAL_EQUAL, Token.TokenType.BANG_EQUAL)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), comparison(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr comparison() {
        Expr expression = term();
        while (match(Token.TokenType.LESS, Token.TokenType.LESS_EQUAL, Token.TokenType.GREATER,
                Token.TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), term(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr term() {
        Expr expression = factor();
        while (match(Token.TokenType.PLUS, Token.TokenType.MINUS)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), factor(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr factor() {
        Expr expression = unary();
        while (match(Token.TokenType.STAR, Token.TokenType.SLASH, Token.TokenType.PERCENT)) {
            Token operator = previous();
            expression = new Expr.Binary(expression, operator.text(), unary(), operator.line(), operator.column());
        }
        return expression;
    }

    private Expr unary() {
        if (match(Token.TokenType.BANG, Token.TokenType.MINUS)) {
            Token operator = previous();
            return new Expr.Unary(operator.text(), unary(), operator.line(), operator.column());
        }
        return call();
    }

    private Expr call() {
        if (check(Token.TokenType.IDENTIFIER) && checkNext(Token.TokenType.LPAREN)) {
            Token name = advance();
            expect(Token.TokenType.LPAREN, "Expected '('");
            List<Expr> arguments = new ArrayList<>();
            if (!check(Token.TokenType.RPAREN)) {
                do {
                    arguments.add(expression());
                } while (match(Token.TokenType.COMMA));
            }
            expect(Token.TokenType.RPAREN, "Expected ')'");
            return new Expr.Call(name.text(), arguments, name.line(), name.column());
        }
        return primary();
    }

    private Expr primary() {
        if (match(Token.TokenType.TRUE)) {
            return new Expr.Literal(new Value.BoolValue(true), previous().line(), previous().column());
        }
        if (match(Token.TokenType.FALSE)) {
            return new Expr.Literal(new Value.BoolValue(false), previous().line(), previous().column());
        }
        if (match(Token.TokenType.INTEGER)) {
            try {
                return new Expr.Literal(new Value.IntValue(Long.parseLong(previous().text())),
                        previous().line(), previous().column());
            } catch (NumberFormatException exception) {
                throw error(previous(), "Integer is too large");
            }
        }
        if (match(Token.TokenType.DECIMAL)) {
            try {
                return new Expr.Literal(new Value.DoubleValue(Double.parseDouble(previous().text())),
                        previous().line(), previous().column());
            } catch (NumberFormatException exception) {
                throw error(previous(), "Invalid decimal");
            }
        }
        if (match(Token.TokenType.STRING)) {
            return new Expr.TemplateExpr(Template.parse(previous().text(), previous().line(), previous().column()),
                    previous().line(), previous().column());
        }
        if (match(Token.TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous().text(), previous().line(), previous().column());
        }
        if (match(Token.TokenType.LPAREN)) {
            Expr expression = expression();
            expect(Token.TokenType.RPAREN, "Expected ')'");
            return expression;
        }
        throw error(peek(), "Expected an expression");
    }

    private boolean matchIdentifier(String text) {
        if (check(Token.TokenType.IDENTIFIER) && peek().text().equals(text)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token expect(Token.TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private boolean check(Token.TokenType type) {
        return peek().type() == type;
    }

    private boolean checkNext(Token.TokenType type) {
        return current + 1 < tokens.size() && tokens.get(current + 1).type() == type;
    }

    private Token advance() {
        if (!check(Token.TokenType.EOF)) {
            current++;
        }
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(Math.max(0, current - 1));
    }

    private static ScriptException error(Token token, String message) {
        return new ScriptException(token.line(), token.column(), message);
    }
}
