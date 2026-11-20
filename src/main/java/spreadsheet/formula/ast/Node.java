package spreadsheet.formula.ast;

import java.util.List;
import spreadsheet.model.CellAddress;

/** The syntax tree of a formula. */
public sealed interface Node {

    record NumberNode(double value) implements Node {}

    record CellRefNode(CellAddress address) implements Node {}

    /** Only valid as a function argument, e.g. SUM(A1:B5). */
    record RangeNode(CellAddress from, CellAddress to) implements Node {}

    record UnaryOpNode(char op, Node operand) implements Node {}

    record BinaryOpNode(char op, Node left, Node right) implements Node {}

    record FunctionCallNode(String name, List<Node> args) implements Node {
        public FunctionCallNode {
            args = List.copyOf(args);
        }
    }
}
