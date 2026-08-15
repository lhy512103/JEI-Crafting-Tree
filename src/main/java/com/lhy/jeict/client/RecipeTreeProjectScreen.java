package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.List;

import com.lhy.jeict.recipe_tree.RecipeTreeCopies;
import com.lhy.jeict.recipe_tree.RecipeTreeProjectManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full project editor for multi-target planning. */
public final class RecipeTreeProjectScreen extends Screen {
    private final Screen parent;
    private final RecipeTreeProjectManager projects;
    private final Runnable beforeChange;
    private final Runnable changed;
    private EditBox nameBox;
    private EditBox amountBox;
    private int selected;

    public RecipeTreeProjectScreen(Screen parent, RecipeTreeProjectManager projects, Runnable beforeChange,
            Runnable changed) {
        super(Component.translatable("gui.jeict.recipe_tree.projects_title"));
        this.parent = parent;
        this.projects = projects;
        this.beforeChange = beforeChange;
        this.changed = changed;
    }

    @Override
    protected void init() {
        int center = width / 2;
        nameBox = new EditBox(font, center - 150, 42, 190, 20,
                Component.translatable("gui.jeict.recipe_tree.project_name"));
        nameBox.setHint(Component.translatable("gui.jeict.recipe_tree.project_name_hint"));
        amountBox = new EditBox(font, center + 48, 42, 102, 20,
                Component.translatable("gui.jeict.recipe_tree.project_amount"));
        amountBox.setFilter(value -> value.isBlank() || value.chars().allMatch(Character::isDigit));
        amountBox.setValue("1");
        addRenderableWidget(nameBox);
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.jeict.recipe_tree.project_add"), b -> addProject())
                .bounds(center - 150, 68, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.jeict.recipe_tree.project_set_amount"), b -> setAmount())
                .bounds(center - 50, 68, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.jeict.recipe_tree.project_remove"), b -> removeProject())
                .bounds(center + 76, 68, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.jeict.recipe_tree.back"), b -> onClose())
                .bounds(center - 50, height - 30, 100, 20).build());
        selected = Math.max(0, names().indexOf(projects.activeProject()));
        syncSelection();
    }

    private List<String> names() { return new ArrayList<>(projects.roots().keySet()); }

    private void addProject() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) name = "project-" + (projects.roots().size() + 1);
        beforeChange.run();
        projects.addOrReplace(name, RecipeTreeCopies.deepCopy(projects.activeRoot()), parseAmount());
        selected = Math.max(0, names().indexOf(name));
        changed.run();
        syncSelection();
    }

    private void setAmount() {
        List<String> names = names();
        if (names.isEmpty()) return;
        String name = names.get(Math.min(selected, names.size() - 1));
        beforeChange.run();
        projects.setAmount(name, parseAmount());
        changed.run();
        syncSelection();
    }

    private void removeProject() {
        List<String> names = names();
        if (names.isEmpty()) return;
        String name = names.get(Math.min(selected, names.size() - 1));
        if ("default".equals(name)) return;
        beforeChange.run();
        projects.remove(name);
        selected = Math.min(selected, Math.max(0, names().size() - 2));
        changed.run();
        syncSelection();
    }

    private long parseAmount() {
        try { return Math.max(1L, Long.parseLong(amountBox.getValue())); }
        catch (NumberFormatException ignored) { return 1L; }
    }

    private void syncSelection() {
        List<String> names = names();
        if (names.isEmpty()) return;
        selected = Math.max(0, Math.min(selected, names.size() - 1));
        String name = names.get(selected);
        projects.select(name);
        nameBox.setValue(name);
        amountBox.setValue(Long.toString(projects.amounts().getOrDefault(name, 1L)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = width / 2 - 150;
        int top = 104;
        List<String> names = names();
        for (int i = 0; i < names.size(); i++) {
            int y = top + i * 24;
            if (mouseX >= left && mouseX <= left + 300 && mouseY >= y && mouseY < y + 22) {
                if (selected != i) {
                    beforeChange.run();
                    selected = i;
                    syncSelection();
                    changed.run();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 150;
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        List<String> names = names();
        for (int i = 0; i < names.size(); i++) {
            int y = 104 + i * 24;
            int color = i == selected ? 0xFF406080 : 0xFF202830;
            graphics.fill(left, y, left + 300, y + 22, color);
            String name = names.get(i);
            String line = name + "  × " + projects.amounts().getOrDefault(name, 1L)
                    + (name.equals(projects.activeProject()) ? "  ✓" : "");
            graphics.drawString(font, line, left + 7, y + 7, 0xFFFFFF, false);
        }
    }

    @Override
    public void onClose() {
        projects.select(names().isEmpty() ? "default" : names().get(Math.min(selected, names().size() - 1)));
        changed.run();
        minecraft.setScreen(parent);
    }
}
