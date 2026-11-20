package spreadsheet.formula;

import java.util.ArrayList;
import java.util.List;
import spreadsheet.formula.Token.Type;

/** Turns formula text (without the leading '=') into tokens. */
public final class Tokenizer {
    private Tokenizer() {}

    public static List<Token> tokenize(String text) throws ParseException {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) i++;
                String s = text.substring(start, i);
                try {
                    tokens.add(new Token(Type.NUMBER, s, Double.parseDouble(s)));
                } catch (NumberFormatException e) {
                    throw new ParseException("bad number '" + s + "'");
                }
            } else if (Character.isLetter(c)) {
                // Letters followed by digits is a cell reference (B12); letters only is a name (SUM).
                int start = i;
                while (i < text.length() && Character.isLetter(text.charAt(i))) i++;
                int lettersEnd = i;
                while (i < text.length() && Character.isDigit(text.charAt(i))) i++;
                String s = text.substring(start, i).toUpperCase();
                if (i < text.length() && Character.isLetter(text.charAt(i))) {
                    throw new ParseException("bad name '" + text.substring(start, i + 1) + "'");
                }
                tokens.add(Token.of(i > lettersEnd ? Type.CELL : Type.NAME, s));
            } else {
                Type type = switch (c) {
                    case '+' -> Type.PLUS;
                    case '-' -> Type.MINUS;
                    case '*' -> Type.STAR;
                    case '/' -> Type.SLASH;
                    case '^' -> Type.CARET;
                    case '(' -> Type.LPAREN;
                    case ')' -> Type.RPAREN;
                    case ',' -> Type.COMMA;
                    case ':' -> Type.COLON;
                    default -> throw new ParseException("unexpected character '" + c + "'");
                };
                tokens.add(Token.of(type, String.valueOf(c)));
                i++;
            }
        }
        tokens.add(Token.of(Type.EOF, ""));
        return tokens;
    }
}
