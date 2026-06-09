package com.lhy.jeict.tree;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RecipeTree {
    private final TreeNode root;

    public RecipeTree(TreeNode root) {
        this.root = root;
    }

    public TreeNode root() {
        return root;
    }

    public List<TreeNode> visibleNodes() {
        List<TreeNode> nodes = new ArrayList<>();
        collect(root, nodes);
        return nodes;
    }

    public ItemStack goalStack() {
        return root.displayStack();
    }

    private static void collect(TreeNode node, List<TreeNode> nodes) {
        nodes.add(node);
        if (!node.expanded()) {
            return;
        }
        for (TreeNode child : node.children()) {
            collect(child, nodes);
        }
    }
}
