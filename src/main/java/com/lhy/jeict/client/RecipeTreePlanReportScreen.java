package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lhy.jeict.api.CraftingTreeInventorySources;
import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.planning.ExecutionChecklist;
import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.PlanTarget;
import com.lhy.jeict.planning.RecipePlanResult;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.planning.RecipeRouteComparator;
import com.lhy.jeict.planning.RouteScore;
import com.lhy.jeict.planning.SubstitutionStrategy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable human-facing projection of the global plan. */
public final class RecipeTreePlanReportScreen extends Screen {
    private enum Tab { MATERIALS, CHECKLIST, MACHINES, ROUTES }
    private final Screen parent;
    private final RecipePlanResult result;
    private final List<PlanTarget> targets;
    private final InventorySnapshot inventory;
    private final SubstitutionStrategy currentStrategy;
    private volatile Thread routeWorker;
    private Tab tab = Tab.MATERIALS;
    private int scroll;
    private long routeGeneration;
    private volatile List<RouteLine> routes = List.of();

    public RecipeTreePlanReportScreen(Screen parent, RecipePlanResult result, List<PlanTarget> targets,
            InventorySnapshot inventory, SubstitutionStrategy currentStrategy) {
        super(Component.translatable("gui.jeict.recipe_tree.plan_report"));
        this.parent = parent;
        this.result = result;
        this.targets = List.copyOf(targets);
        this.inventory = inventory;
        this.currentStrategy = currentStrategy;
    }

    @Override
    protected void init() {
        int left = width / 2 - 210;
        addRenderableWidget(tabButton(left, Tab.MATERIALS, "gui.jeict.recipe_tree.plan_materials"));
        addRenderableWidget(tabButton(left + 106, Tab.CHECKLIST, "gui.jeict.recipe_tree.plan_checklist"));
        addRenderableWidget(tabButton(left + 212, Tab.MACHINES, "gui.jeict.recipe_tree.plan_machines"));
        addRenderableWidget(tabButton(left + 318, Tab.ROUTES, "gui.jeict.recipe_tree.plan_routes"));
        addRenderableWidget(Button.builder(Component.translatable("gui.jeict.recipe_tree.back"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private Button tabButton(int x, Tab target, String key) {
        return Button.builder(Component.translatable(key), b -> {
            tab = target;
            scroll = 0;
            if (target == Tab.ROUTES && routes.isEmpty()) compareRoutesAsync();
        }).bounds(x, 34, 102, 20).build();
    }

    private void compareRoutesAsync() {
        long generation = ++routeGeneration;
        routes = List.of(new RouteLine(Component.translatable("gui.jeict.recipe_tree.plan_calculating").getString(), null, false));
        Thread previous = routeWorker;
        if (previous != null) previous.interrupt();
        routeWorker = new Thread(() -> {
            List<RecipePlanResult> results = new ArrayList<>();
            List<SubstitutionStrategy> strategies = List.of(SubstitutionStrategy.values());
            for (SubstitutionStrategy strategy : strategies) {
                if (generation != routeGeneration || Thread.currentThread().isInterrupted()) return;
                if (strategy == currentStrategy) {
                    results.add(result);
                } else {
                    results.add(new RecipePlanSolver(strategy, RecipeTreeConfig.PREFERRED_NAMESPACE.get())
                            .solve(targets, inventory));
                }
                Thread.yield();
            }
            RecipeRouteComparator.Comparison comparison = RecipeRouteComparator.compare(results);
            List<RouteLine> built = new ArrayList<>();
            for (int i = 0; i < strategies.size(); i++) {
                built.add(new RouteLine(strategies.get(i).name(), comparison.scores().get(i),
                        i == comparison.recommendedIndex()));
            }
            minecraft.execute(() -> {
                if (generation == routeGeneration) routes = List.copyOf(built);
            });
        }, "jeict-route-comparison");
        routeWorker.start();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int left = width / 2 - 210;
        int right = width / 2 + 210;
        int top = 62;
        graphics.fill(left, top, right, height - 36, 0xD0182028);
        List<String> lines = lines();
        int visible = Math.max(1, (height - top - 48) / 13);
        scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            String line = lines.get(scroll + i);
            graphics.drawString(font, font.plainSubstrByWidth(line, right - left - 16), left + 8, top + 8 + i * 13,
                    0xFFE4E8EC, false);
        }
    }

    private List<String> lines() {
        return switch (tab) {
            case MATERIALS -> materialLines();
            case CHECKLIST -> ExecutionChecklist.lines(result);
            case MACHINES -> result.machineRuns().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "  × " + entry.getValue()).toList();
            case ROUTES -> routes.stream().map(RouteLine::format).toList();
        };
    }

    private List<String> materialLines() {
        List<String> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.jeict.recipe_tree.plan_raw_heading", result.totalRawUnits()).getString());
        result.rawRequirements().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("  " + entry.getKey().encoded() + "  × " + entry.getValue()));
        lines.add("");
        lines.add(Component.translatable("gui.jeict.recipe_tree.plan_inventory_heading").getString());
        result.inventoryUsed().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("  " + entry.getKey().encoded() + "  × " + entry.getValue()));
        lines.add("");
        lines.add(Component.translatable("gui.jeict.recipe_tree.plan_inventory_sources").getString());
        CraftingTreeInventorySources.statuses().forEach(status -> lines.add("  " + status.id() + "  "
                + (status.available() ? "available" : "unavailable") + "  p=" + status.priority()
                + (status.error().isEmpty() ? "" : "  error=" + status.error())));
        lines.add("");
        lines.add(Component.translatable("gui.jeict.recipe_tree.plan_surplus_heading", result.totalWasteUnits()).getString());
        result.surplus().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("  " + entry.getKey().encoded() + "  × " + entry.getValue()));
        if (!result.cycleDiagnostics().isEmpty()) {
            lines.add("");
            lines.add(Component.translatable("gui.jeict.recipe_tree.plan_cycles").getString());
            result.cycleDiagnostics().stream().sorted().forEach(line -> lines.add("  " + line));
        }
        return lines;
    }

    @Override
    public void onClose() {
        routeGeneration++;
        Thread worker = routeWorker;
        if (worker != null) worker.interrupt();
        routeWorker = null;
        minecraft.setScreen(parent);
    }

    private record RouteLine(String name, RouteScore score, boolean recommended) {
        String format() {
            if (score == null) return name;
            return (recommended ? "★ " : "  ") + name + "  raw=" + score.rawUnits() + " runs="
                    + score.machineRuns() + " machines=" + score.distinctMachines() + " waste=" + score.wasteUnits()
                    + " steps=" + score.steps();
        }
    }
}
