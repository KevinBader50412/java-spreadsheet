package spreadsheet.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import spreadsheet.formula.Evaluator;
import spreadsheet.formula.FunctionRegistry;
import spreadsheet.formula.ParseException;
import spreadsheet.formula.Parser;
import spreadsheet.formula.ast.Node;
import spreadsheet.model.Value.*;

/**
 * The spreadsheet model. It has no UI code: the UI registers a listener and is
 * told which cells changed after every edit.
 *
 * <p>Editing a cell:
 * <ol>
 *   <li>parse the new content once (formulas are kept as an AST)
 *   <li>replace the cell's edges in the dependency graph
 *   <li>find the cell and everything that depends on it (BFS)
 *   <li>order that subset with Kahn's topological sort and evaluate each cell once
 *   <li>cells Kahn's algorithm cannot order are on a cycle (or depend on one): #CYCLE!
 * </ol>
 * Only the affected cells are recalculated, so the cost is O(V + E) of that subgraph.
 */
public final class Spreadsheet {
    public static final int DEFAULT_COLUMNS = 26;
    public static final int DEFAULT_ROWS = 100;

    private final int columns;
    private final int rows;
    private final Map<CellAddress, Cell> cells = new HashMap<>();
    private final DependencyGraph graph = new DependencyGraph();
    private final Evaluator evaluator;
    private final List<Consumer<Set<CellAddress>>> listeners = new ArrayList<>();

    private long lastRecalcNanos;
    private int lastCellsEvaluated;

    public Spreadsheet() {
        this(DEFAULT_COLUMNS, DEFAULT_ROWS);
    }

    public Spreadsheet(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        this.evaluator = new Evaluator(new FunctionRegistry(), columns, rows);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    // ---- Reading -----------------------------------------------------------

    public Value valueAt(CellAddress a) {
        Cell c = cells.get(a);
        return c == null ? EmptyValue.INSTANCE : c.value();
    }

    public Value valueAt(String address) {
        return valueAt(CellAddress.parse(address));
    }

    public String rawAt(CellAddress a) {
        Cell c = cells.get(a);
        return c == null ? "" : c.raw();
    }

    public Map<CellAddress, String> nonEmptyCells() {
        Map<CellAddress, String> out = new HashMap<>();
        cells.forEach((a, c) -> out.put(a, c.raw()));
        return out;
    }

    public long lastRecalcNanos() {
        return lastRecalcNanos;
    }

    /** How many cells the last edit (or recalculateAll) evaluated. */
    public int lastCellsEvaluated() {
        return lastCellsEvaluated;
    }

    public void addListener(Consumer<Set<CellAddress>> listener) {
        listeners.add(listener);
    }

    // ---- Editing -----------------------------------------------------------

    public Set<CellAddress> setCell(String address, String raw) {
        return setCell(CellAddress.parse(address), raw);
    }

    /** Sets a cell's content and recalculates what depends on it. Returns the cells recalculated. */
    public Set<CellAddress> setCell(CellAddress a, String raw) {
        if (!evaluator.inGrid(a)) throw new IllegalArgumentException(a + " is outside the grid");
        long start = System.nanoTime();
        raw = raw == null ? "" : raw;

        List<CellAddress> reads = List.of();
        if (raw.isEmpty()) {
            cells.remove(a);
        } else if (raw.startsWith("=")) {
            Node ast = null;
            try {
                ast = Parser.parse(raw.substring(1));
                reads = evaluator.references(ast);
            } catch (ParseException e) {
                // stored as a parse error; the cell shows #PARSE!
            }
            cells.put(a, new Cell(raw, ast, ast == null));
        } else {
            cells.put(a, new Cell(raw, null, false));
        }
        graph.setDependencies(a, reads);

        Set<CellAddress> affected = graph.affectedBy(a);
        recalculate(affected);
        lastRecalcNanos = System.nanoTime() - start;
        notifyListeners(affected);
        return affected;
    }

    /** Empties the sheet. */
    public void clear() {
        Set<CellAddress> old = new HashSet<>(cells.keySet());
        cells.clear();
        graph.clear();
        notifyListeners(old);
    }

    /** Recalculates every cell, ignoring the dependency graph's knowledge of what changed. */
    public void recalculateAll() {
        long start = System.nanoTime();
        recalculate(new HashSet<>(cells.keySet()));
        lastRecalcNanos = System.nanoTime() - start;
    }

    // ---- Recalculation -------------------------------------------------------

    /** Kahn's algorithm over the given subset of cells. */
    private void recalculate(Set<CellAddress> subset) {
        // In-degree = how many of the cell's inputs are also in the subset and not yet computed.
        Map<CellAddress, Integer> inDegree = new HashMap<>();
        ArrayDeque<CellAddress> ready = new ArrayDeque<>();
        for (CellAddress c : subset) {
            int n = 0;
            for (CellAddress d : graph.dependenciesOf(c)) if (subset.contains(d)) n++;
            inDegree.put(c, n);
            if (n == 0) ready.add(c);
        }

        int evaluated = 0;
        while (!ready.isEmpty()) {
            CellAddress c = ready.poll();
            evaluate(c);
            evaluated++;
            for (CellAddress dependent : graph.dependentsOf(c)) {
                if (!subset.contains(dependent)) continue;
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) ready.add(dependent);
            }
        }

        // Whatever never reached in-degree 0 sits on a cycle or reads from one.
        if (evaluated < subset.size()) {
            for (CellAddress c : subset) {
                if (inDegree.get(c) > 0) {
                    Cell cell = cells.get(c);
                    if (cell != null) cell.setValue(ErrorValue.CYCLE);
                }
            }
        }
        lastCellsEvaluated = evaluated;
    }

    private void evaluate(CellAddress a) {
        Cell cell = cells.get(a);
        if (cell == null) return;  // an empty cell that something reads
        Value v;
        if (cell.hasParseError()) {
            v = ErrorValue.PARSE;
        } else if (cell.isFormula()) {
            v = evaluator.evaluate(cell.formula(), this::valueAt);
        } else {
            v = literal(cell.raw());
        }
        cell.setValue(v);
    }

    private static Value literal(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return new TextValue(raw);
        try {
            return new NumberValue(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return new TextValue(raw);
        }
    }

    private void notifyListeners(Set<CellAddress> changed) {
        for (Consumer<Set<CellAddress>> l : listeners) l.accept(changed);
    }
}
