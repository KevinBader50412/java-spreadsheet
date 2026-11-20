package spreadsheet.formula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import spreadsheet.model.Value;
import spreadsheet.model.Value.*;

/**
 * Built-in functions by name. Adding a function means adding one entry here;
 * the parser accepts any name and the evaluator looks it up at run time.
 */
public final class FunctionRegistry {

    /**
     * A function receives its arguments with ranges already expanded into
     * individual cell values, in order.
     */
    @FunctionalInterface
    public interface SpreadsheetFunction {
        Value apply(List<Value> args);
    }

    private final Map<String, SpreadsheetFunction> functions = new HashMap<>();

    public FunctionRegistry() {
        register("SUM", args -> numbers(args, (n, sum) -> sum + n, 0));
        register("MIN", args -> extreme(args, true));
        register("MAX", args -> extreme(args, false));
        register("COUNT", args -> {
            if (firstError(args) instanceof ErrorValue e) return e;
            return new NumberValue(args.stream().filter(v -> v instanceof NumberValue).count());
        });
        register("AVERAGE", args -> {
            if (firstError(args) instanceof ErrorValue e) return e;
            double[] s = sumAndCount(args);
            return s[1] == 0 ? ErrorValue.DIV_ZERO : new NumberValue(s[0] / s[1]);
        });
        register("ABS", args -> {
            if (args.size() != 1) return ErrorValue.VALUE;
            Value x = Evaluator.toNumber(args.get(0));
            return x instanceof NumberValue n ? new NumberValue(Math.abs(n.number())) : x;
        });
        register("ROUND", args -> {
            if (args.size() != 2) return ErrorValue.VALUE;
            Value x = Evaluator.toNumber(args.get(0));
            Value d = Evaluator.toNumber(args.get(1));
            if (!(x instanceof NumberValue n)) return x;
            if (!(d instanceof NumberValue digits)) return d;
            double scale = Math.pow(10, (int) digits.number());
            return new NumberValue(Math.round(n.number() * scale) / scale);
        });
    }

    public void register(String name, SpreadsheetFunction fn) {
        functions.put(name.toUpperCase(), fn);
    }

    /** Null if unknown (the cell shows #NAME?). */
    public SpreadsheetFunction get(String name) {
        return functions.get(name.toUpperCase());
    }

    // Aggregates follow spreadsheet convention: text and empty cells are skipped,
    // and any error among the arguments is the result.

    private static Value firstError(List<Value> args) {
        for (Value v : args) if (v instanceof ErrorValue) return v;
        return null;
    }

    private interface Fold {
        double apply(double n, double acc);
    }

    private static Value numbers(List<Value> args, Fold fold, double start) {
        if (firstError(args) instanceof ErrorValue e) return e;
        double acc = start;
        for (Value v : args) if (v instanceof NumberValue n) acc = fold.apply(n.number(), acc);
        return new NumberValue(acc);
    }

    private static double[] sumAndCount(List<Value> args) {
        double sum = 0, count = 0;
        for (Value v : args) {
            if (v instanceof NumberValue n) {
                sum += n.number();
                count++;
            }
        }
        return new double[] {sum, count};
    }

    private static Value extreme(List<Value> args, boolean min) {
        if (firstError(args) instanceof ErrorValue e) return e;
        Double best = null;
        for (Value v : args) {
            if (v instanceof NumberValue n && (best == null || (min ? n.number() < best : n.number() > best))) {
                best = n.number();
            }
        }
        return new NumberValue(best == null ? 0 : best);
    }
}
