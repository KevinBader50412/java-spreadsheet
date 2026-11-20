package spreadsheet.formula;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import spreadsheet.formula.ast.Node;
import spreadsheet.formula.ast.Node.*;
import spreadsheet.model.CellAddress;
import spreadsheet.model.Value;
import spreadsheet.model.Value.*;

/**
 * Walks a formula's AST and computes its value. Errors are values too: once a
 * sub-expression produces one, it is passed up unchanged, so an error in A1
 * shows up in every formula that uses A1.
 */
public final class Evaluator {

    private final FunctionRegistry functions;
    private final int columns;
    private final int rows;

    public Evaluator(FunctionRegistry functions, int columns, int rows) {
        this.functions = functions;
        this.columns = columns;
        this.rows = rows;
    }

    /** {@code lookup} returns the current value of a cell inside the grid. */
    public Value evaluate(Node node, Function<CellAddress, Value> lookup) {
        return switch (node) {
            case NumberNode n -> new NumberValue(n.value());
            case CellRefNode ref -> inGrid(ref.address()) ? lookup.apply(ref.address()) : ErrorValue.REF;
            case RangeNode r -> ErrorValue.VALUE;  // a range outside a function call
            case UnaryOpNode u -> {
                Value v = toNumber(evaluate(u.operand(), lookup));
                yield v instanceof NumberValue n ? new NumberValue(-n.number()) : v;
            }
            case BinaryOpNode b -> binary(b.op(), evaluate(b.left(), lookup), evaluate(b.right(), lookup));
            case FunctionCallNode f -> call(f, lookup);
        };
    }

    private Value binary(char op, Value leftValue, Value rightValue) {
        Value l = toNumber(leftValue);
        if (!(l instanceof NumberValue a)) return l;
        Value r = toNumber(rightValue);
        if (!(r instanceof NumberValue b)) return r;
        double x = a.number(), y = b.number();
        if (op == '/' && y == 0) return ErrorValue.DIV_ZERO;
        double result = switch (op) {
            case '+' -> x + y;
            case '-' -> x - y;
            case '*' -> x * y;
            case '/' -> x / y;
            case '^' -> Math.pow(x, y);
            default -> throw new IllegalStateException("unknown operator " + op);
        };
        // e.g. (-8)^0.5 or an overflow
        if (Double.isNaN(result) || Double.isInfinite(result)) return ErrorValue.VALUE;
        return new NumberValue(result);
    }

    private Value call(FunctionCallNode f, Function<CellAddress, Value> lookup) {
        FunctionRegistry.SpreadsheetFunction fn = functions.get(f.name());
        if (fn == null) return ErrorValue.NAME;
        List<Value> args = new ArrayList<>();
        for (Node arg : f.args()) {
            if (arg instanceof RangeNode r) {
                if (!inGrid(r.from()) || !inGrid(r.to())) return ErrorValue.REF;
                for (CellAddress a : expand(r)) args.add(lookup.apply(a));
            } else {
                args.add(evaluate(arg, lookup));
            }
        }
        return fn.apply(args);
    }

    /** For arithmetic: empty counts as 0, text is #VALUE!, errors pass through. */
    static Value toNumber(Value v) {
        return switch (v) {
            case NumberValue n -> n;
            case EmptyValue e -> new NumberValue(0);
            case TextValue t -> ErrorValue.VALUE;
            case ErrorValue e -> e;
        };
    }

    public boolean inGrid(CellAddress a) {
        return a.col() < columns && a.row() < rows;
    }

    /** All cells of a range, in either corner order. */
    public static List<CellAddress> expand(RangeNode r) {
        int c1 = Math.min(r.from().col(), r.to().col()), c2 = Math.max(r.from().col(), r.to().col());
        int r1 = Math.min(r.from().row(), r.to().row()), r2 = Math.max(r.from().row(), r.to().row());
        List<CellAddress> cells = new ArrayList<>();
        for (int row = r1; row <= r2; row++) {
            for (int col = c1; col <= c2; col++) cells.add(new CellAddress(col, row));
        }
        return cells;
    }

    /** Every cell a formula reads, with ranges expanded. Cells outside the grid are left out. */
    public List<CellAddress> references(Node node) {
        List<CellAddress> out = new ArrayList<>();
        collect(node, out);
        return out;
    }

    private void collect(Node node, List<CellAddress> out) {
        switch (node) {
            case NumberNode n -> { }
            case CellRefNode ref -> { if (inGrid(ref.address())) out.add(ref.address()); }
            case RangeNode r -> {
                if (inGrid(r.from()) && inGrid(r.to())) out.addAll(expand(r));
            }
            case UnaryOpNode u -> collect(u.operand(), out);
            case BinaryOpNode b -> {
                collect(b.left(), out);
                collect(b.right(), out);
            }
            case FunctionCallNode f -> f.args().forEach(a -> collect(a, out));
        }
    }
}
