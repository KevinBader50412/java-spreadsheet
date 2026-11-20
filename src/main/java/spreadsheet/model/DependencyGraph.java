package spreadsheet.model;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which cells read which.
 *
 * <ul>
 *   <li>{@code dependencies}: B1 → {A1} when B1 = A1 + 1 (what a formula reads)
 *   <li>{@code dependents}: A1 → {B1} (the reverse: who must be recalculated when A1 changes)
 * </ul>
 */
public final class DependencyGraph {
    private final Map<CellAddress, Set<CellAddress>> dependencies = new HashMap<>();
    private final Map<CellAddress, Set<CellAddress>> dependents = new HashMap<>();

    /** Replaces the cell's outgoing edges: old edges are removed first, then the new ones added. */
    public void setDependencies(CellAddress cell, Collection<CellAddress> reads) {
        Set<CellAddress> old = dependencies.remove(cell);
        if (old != null) {
            for (CellAddress d : old) {
                Set<CellAddress> back = dependents.get(d);
                back.remove(cell);
                if (back.isEmpty()) dependents.remove(d);
            }
        }
        if (reads.isEmpty()) return;
        Set<CellAddress> deps = new HashSet<>(reads);
        dependencies.put(cell, deps);
        for (CellAddress d : deps) dependents.computeIfAbsent(d, k -> new HashSet<>()).add(cell);
    }

    public Set<CellAddress> dependenciesOf(CellAddress cell) {
        return dependencies.getOrDefault(cell, Set.of());
    }

    public Set<CellAddress> dependentsOf(CellAddress cell) {
        return dependents.getOrDefault(cell, Set.of());
    }

    /** The cell plus everything that depends on it directly or indirectly (BFS). */
    public Set<CellAddress> affectedBy(CellAddress start) {
        Set<CellAddress> seen = new LinkedHashSet<>();
        ArrayDeque<CellAddress> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (CellAddress d : dependentsOf(queue.poll())) {
                if (seen.add(d)) queue.add(d);
            }
        }
        return seen;
    }

    public void clear() {
        dependencies.clear();
        dependents.clear();
    }
}
