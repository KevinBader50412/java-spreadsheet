package spreadsheet.formula;

import java.util.ArrayList;
import java.util.List;
import spreadsheet.formula.Token.Type;
import spreadsheet.formula.ast.Node;
import spreadsheet.formula.ast.Node.*;
import spreadsheet.model.CellAddress;

/**
 * Recursive descent parser, one method per grammar rule:
 *
 * <pre>
 * expr    := term (('+'|'-') term)*
 * term    := power (('*'|'/') power)*
 * power   := unary ('^' power)?          right-associative: 2^3^2 = 2^9
 * unary   := '-' unary | primary
 * primary := NUMBER | CELL | NAME '(' args ')' | '(' expr ')'
 * args    := arg (',' arg)*
 * arg     := CELL ':' CELL | expr
 * </pre>
 *
 * Lower rules bind tighter, so precedence falls out of the call structure.
 */
public final class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Parses formula text without the leading '='. */
    public static Node parse(String formula) throws ParseException {
        Parser p = new Parser(Tokenizer.tokenize(formula));
        Node node = p.expr();
        if (p.peek().type() != Type.EOF) throw new ParseException("unexpected '" + p.peek().text() + "'");
        return node;
    }

    private Node expr() throws ParseException {
        Node left = term();
        while (peek().type() == Type.PLUS || peek().type() == Type.MINUS) {
            char op = next().text().charAt(0);
            left = new BinaryOpNode(op, left, term());
        }
        return left;
    }

    private Node term() throws ParseException {
        Node left = power();
        while (peek().type() == Type.STAR || peek().type() == Type.SLASH) {
            char op = next().text().charAt(0);
            left = new BinaryOpNode(op, left, power());
        }
        return left;
    }

    private Node power() throws ParseException {
        Node base = unary();
        if (peek().type() == Type.CARET) {
            next();
            return new BinaryOpNode('^', base, power());  // recursion on the right = right-associative
        }
        return base;
    }

    private Node unary() throws ParseException {
        if (peek().type() == Type.MINUS) {
            next();
            return new UnaryOpNode('-', unary());
        }
        return primary();
    }

    private Node primary() throws ParseException {
        Token t = next();
        return switch (t.type()) {
            case NUMBER -> new NumberNode(t.number());
            case CELL -> new CellRefNode(address(t));
            case NAME -> {
                expect(Type.LPAREN);
                List<Node> args = args();
                expect(Type.RPAREN);
                yield new FunctionCallNode(t.text(), args);
            }
            case LPAREN -> {
                Node inner = expr();
                expect(Type.RPAREN);
                yield inner;
            }
            default -> throw new ParseException("unexpected '" + t.text() + "'");
        };
    }

    private List<Node> args() throws ParseException {
        List<Node> args = new ArrayList<>();
        if (peek().type() == Type.RPAREN) return args;  // f()
        args.add(arg());
        while (peek().type() == Type.COMMA) {
            next();
            args.add(arg());
        }
        return args;
    }

    private Node arg() throws ParseException {
        if (peek().type() == Type.CELL && peekAhead().type() == Type.COLON) {
            CellAddress from = address(next());
            next();  // ':'
            Token to = next();
            if (to.type() != Type.CELL) throw new ParseException("range needs a cell after ':'");
            return new RangeNode(from, address(to));
        }
        return expr();
    }

    private static CellAddress address(Token t) throws ParseException {
        try {
            return CellAddress.parse(t.text());
        } catch (IllegalArgumentException e) {
            throw new ParseException("bad cell reference '" + t.text() + "'");  // e.g. A0
        }
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peekAhead() {
        return tokens.get(Math.min(pos + 1, tokens.size() - 1));
    }

    private Token next() {
        Token t = tokens.get(pos);
        if (t.type() != Type.EOF) pos++;
        return t;
    }

    private void expect(Type type) throws ParseException {
        Token t = next();
        if (t.type() != type) throw new ParseException("expected " + type + " but found '" + t.text() + "'");
    }
}
