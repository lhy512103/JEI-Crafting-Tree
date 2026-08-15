package com.lhy.jeict.recipe_tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Session-scoped multi-target project registry. Each target shares the global planner ledger. */
public final class RecipeTreeProjectManager {
    private final LinkedHashMap<String, RecipeTreeNodeViewModel> roots = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> amounts = new LinkedHashMap<>();
    private String activeProject = "default";

    public RecipeTreeProjectManager(RecipeTreeNodeViewModel root) {
        addOrReplace("default", root, 1L);
    }

    public void addOrReplace(String project, RecipeTreeNodeViewModel root, long amount) {
        if (project == null || project.isBlank() || root == null) return;
        roots.put(project, root);
        amounts.put(project, Math.max(1L, amount));
        activeProject = project;
    }

    public void remove(String project) {
        if (project == null || "default".equals(project)) return;
        roots.remove(project);
        amounts.remove(project);
        if (project.equals(activeProject)) activeProject = roots.keySet().stream().findFirst().orElse("default");
    }

    public void setAmount(String project, long amount) {
        if (roots.containsKey(project)) amounts.put(project, Math.max(1L, amount));
    }

    public void select(String project) {
        if (roots.containsKey(project)) activeProject = project;
    }

    public String activeProject() { return activeProject; }
    public Map<String, RecipeTreeNodeViewModel> roots() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(roots));
    }
    public Map<String, Long> amounts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }
    public RecipeTreeNodeViewModel activeRoot() { return roots.get(activeProject); }

    public Snapshot snapshot() {
        LinkedHashMap<String, RecipeTreeNodeViewModel> copiedRoots = new LinkedHashMap<>();
        roots.forEach((name, root) -> copiedRoots.put(name, RecipeTreeCopies.deepCopy(root)));
        return new Snapshot(copiedRoots, new LinkedHashMap<>(amounts), activeProject);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.roots().isEmpty()) return;
        roots.clear();
        amounts.clear();
        snapshot.roots().forEach((name, root) -> roots.put(name, RecipeTreeCopies.deepCopy(root)));
        amounts.putAll(snapshot.amounts());
        activeProject = roots.containsKey(snapshot.activeProject())
                ? snapshot.activeProject()
                : roots.keySet().iterator().next();
    }

    public record Snapshot(Map<String, RecipeTreeNodeViewModel> roots, Map<String, Long> amounts,
            String activeProject) {
        public Snapshot {
            roots = Collections.unmodifiableMap(new LinkedHashMap<>(roots));
            amounts = Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
        }
    }
}
