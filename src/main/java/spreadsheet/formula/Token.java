package spreadsheet.formula;

/** One token of a formula. {@code number} is only meaningful for NUMBER tokens. */
public record Token(Type type, String text, double number) {

    public enum Type {
        NUMBER,     // 3, 2.5, .5
        CELL,       // A1, b12
        NAME,       // SUM, round
        PLUS, MINUS, STAR, SLASH, CARET,
        LPAREN, RPAREN, COMMA, COLON,
        EOF
    }

    public static Token of(Type type, String text) {
        return new Token(type, text, 0);
    }
}
