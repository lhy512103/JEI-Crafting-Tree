package com.lhy.jeict.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;

/**
 * Session-scoped spatial workspace for independent recipe trees.
 *
 * <p>Every grid cell owns a complete {@link RecipeTreeRootContext}; material ledgers,
 * surplus, projects, pattern drafts and edit history therefore never leak between trees.
 */
public final class RecipeTreeWorkspaceSession {
    private final LinkedHashMap<GridPosition, RecipeTreeRootContext> trees = new LinkedHashMap<>();
    private GridPosition activePosition = GridPosition.ORIGIN;

    public RecipeTreeWorkspaceSession(RecipeTreeRootContext initialTree) {
        trees.put(GridPosition.ORIGIN, initialTree);
    }

    public synchronized RecipeTreeRootContext activeTree() {
        return trees.get(activePosition);
    }

    public synchronized GridPosition activePosition() {
        return activePosition;
    }

    public synchronized void activate(RecipeTreeRootContext tree) {
        if (tree == null) return;
        for (Map.Entry<GridPosition, RecipeTreeRootContext> entry : trees.entrySet()) {
            if (entry.getValue() == tree) {
                activePosition = entry.getKey();
                return;
            }
        }
    }

    public synchronized @Nullable RecipeTreeRootContext neighbor(Direction direction) {
        return neighbor(activePosition, direction);
    }

    public synchronized @Nullable RecipeTreeRootContext neighbor(GridPosition origin, Direction direction) {
        if (origin == null || direction == null) return null;
        return trees.get(origin.move(direction));
    }

    public synchronized boolean addNeighbor(Direction direction, RecipeTreeRootContext tree) {
        return addNeighbor(activePosition, direction, tree);
    }

    public synchronized boolean addNeighbor(GridPosition origin, Direction direction, RecipeTreeRootContext tree) {
        if (origin == null || direction == null || tree == null) return false;
        GridPosition target = origin.move(direction);
        if (trees.containsKey(target)) return false;
        trees.put(target, tree);
        activePosition = target;
        return true;
    }

    public synchronized @Nullable GridPosition positionOf(RecipeTreeRootContext tree) {
        for (Map.Entry<GridPosition, RecipeTreeRootContext> entry : trees.entrySet()) {
            if (entry.getValue() == tree) return entry.getKey();
        }
        return null;
    }

    public synchronized int size() {
        return trees.size();
    }

    public synchronized Map<GridPosition, RecipeTreeRootContext> trees() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(trees));
    }

    public enum Direction {
        UP(0, -1),
        RIGHT(1, 0),
        DOWN(0, 1),
        LEFT(-1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    public record GridPosition(int x, int y) {
        public static final GridPosition ORIGIN = new GridPosition(0, 0);

        public GridPosition move(Direction direction) {
            return new GridPosition(x + direction.dx, y + direction.dy);
        }
    }
}
