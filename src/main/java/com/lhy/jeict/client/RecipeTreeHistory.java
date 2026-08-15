package com.lhy.jeict.client;

import java.util.ArrayDeque;
import java.util.Deque;

import com.lhy.jeict.recipe_tree.RecipeTreeProjectManager;

/** Bounded, in-memory undo/redo history for every project, target amount, and tree selection. */
public final class RecipeTreeHistory {
    private static final int MAX_ENTRIES = 64;
    private final Deque<RecipeTreeProjectManager.Snapshot> undo = new ArrayDeque<>();
    private final Deque<RecipeTreeProjectManager.Snapshot> redo = new ArrayDeque<>();

    public void record(RecipeTreeProjectManager projects) {
        undo.push(projects.snapshot());
        while (undo.size() > MAX_ENTRIES) undo.removeLast();
        redo.clear();
    }

    public boolean undo(RecipeTreeProjectManager projects) {
        if (undo.isEmpty()) return false;
        redo.push(projects.snapshot());
        projects.restore(undo.pop());
        return true;
    }

    public boolean redo(RecipeTreeProjectManager projects) {
        if (redo.isEmpty()) return false;
        undo.push(projects.snapshot());
        projects.restore(redo.pop());
        return true;
    }
}
