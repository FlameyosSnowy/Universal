package io.github.flameyossnowy.universal.sql.params;

import java.util.ArrayList;
import java.util.List;

final class SqlTokenizer {
    private static boolean isKeyword(String s) {
        return switch (s.toLowerCase()) {
            case "and", "or", "in", "like", "select", "from", "where", "exists" -> true;
            default -> false;
        };
    }

    private static boolean isOperatorChar(char c) {
        return "=<>!@#:-+*/|".indexOf(c) >= 0;
    }

    static List<Token> tokenize(String sql) {
        int len = sql.length();
        List<Token> tokens = new ArrayList<>(len / 4);

        for (int i = 0; i < len; ) {
            char c = sql.charAt(i);

            // whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // string literal
            if (c == '\'') {
                int start = i++;
                while (i < len) {
                    if (sql.charAt(i) == '\'' && sql.charAt(i - 1) != '\\') {
                        i++;
                        break;
                    }
                    i++;
                }
                tokens.add(new Token(TokenType.STRING, sql.substring(start, i), start));
                continue;
            }

            // identifier / keyword
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < len && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                String text = sql.substring(start, i);
                tokens.add(new Token(
                    isKeyword(text) ? TokenType.KEYWORD : TokenType.IDENT,
                    text,
                    start
                ));
                continue;
            }

            // parameter
            if (c == '?') {
                tokens.add(new Token(TokenType.QUESTION, "?", i++));
                continue;
            }

            // punctuation
            if (c == '(') { tokens.add(new Token(TokenType.LPAREN, "(", i++)); continue; }
            if (c == ')') { tokens.add(new Token(TokenType.RPAREN, ")", i++)); continue; }
            if (c == ',') { tokens.add(new Token(TokenType.COMMA, ",", i++)); continue; }

            // operators (including JSON)
            if (isOperatorChar(c)) {
                int start = i++;
                while (i < len && isOperatorChar(sql.charAt(i))) i++;
                tokens.add(new Token(TokenType.OPERATOR, sql.substring(start, i), start));
                continue;
            }

            // fallback
            i++;
        }

        return tokens;
    }

    private SqlTokenizer() {}
}
