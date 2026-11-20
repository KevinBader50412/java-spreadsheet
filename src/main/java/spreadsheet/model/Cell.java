package spreadsheet.model;

import spreadsheet.formula.ast.Node;

/**
 * One cell: what the user typed, the parsed formula (if any) and the current value.
 * The formula is parsed once when the cell is edited; recalculation reuses the AST.
 */
public final class Cell {
    private final String raw;
    private final Node formula;         // null unless raw starts with "=" and parsed fine
    private final boolean parseError;
    private Value value = Value.EmptyValue.INSTANCE;

    Cell(String raw, Node formula, boolean parseError) {
        this.raw = raw;
        this.formula = formula;
        this.parseError = parseError;
    }

    public String raw() {
        return raw;
    }

    public Node formula() {
        return formula;
    }

    public boolean isFormula() {
        return raw.startsWith("=");
    }

    boolean hasParseError() {
        return parseError;
    }

    public Value value() {
        return value;
    }

    void setValue(Value value) {
        this.value = value;
    }
}
