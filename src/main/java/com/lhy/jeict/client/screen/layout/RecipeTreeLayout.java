package com.lhy.jeict.client.screen.layout;

import com.lhy.jeict.client.screen.RecipeTreeTheme;
import com.lhy.jeict.tree.TreeNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public final class RecipeTreeLayout {
    private final List<LayoutNode> layoutNodes = new ArrayList<>();
    private final IdentityHashMap<TreeNode, LayoutNode> layoutNodeMap = new IdentityHashMap<>();
    private final IdentityHashMap<TreeNode, Integer> measureCache = new IdentityHashMap<>();
    private double zoom = 1.0;
    private int width;
    private int height;

    public RecipeTreeLayout() {
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    public double zoom() {
        return zoom;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void rebuild(TreeNode root, int panX, int panY) {
        layoutNodes.clear();
        measureCache.clear();
        layoutNodeMap.clear();
        int treeWidth = measure(root);
        int startX = width / 2 - scale(treeWidth) / 2 + panX;
        layout(root, startX, 140 + panY, treeWidth);
    }

    public List<LayoutNode> layoutNodes() {
        return layoutNodes;
    }

    public LayoutNode layoutFor(TreeNode node) {
        return layoutNodeMap.get(node);
    }

    public int scale(int value) {
        return Math.max(1, (int) Math.round(value * zoom));
    }

    public int scaleByZoom(double value) {
        return Math.max(1, (int) Math.round(value * zoom));
    }

    public int unscale(int value) {
        return Math.max(1, (int) Math.floor(value / zoom));
    }

    private int measure(TreeNode node) {
        Integer cached = measureCache.get(node);
        if (cached != null) {
            return cached;
        }
        List<TreeNode> children = visibleChildren(node);
        int result;
        if (children.isEmpty()) {
            result = nodeWidth(node);
        } else {
            int childrenWidth = 0;
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    childrenWidth += RecipeTreeTheme.SIBLING_GAP;
                }
                childrenWidth += measure(children.get(i));
            }
            result = Math.max(nodeWidth(node), childrenWidth);
        }
        measureCache.put(node, result);
        return result;
    }

    private void layout(TreeNode node, int left, int y, int subtreeWidth) {
        int scaledSubtree = scale(subtreeWidth);
        int nodeW = scale(nodeWidth(node));
        int nodeH = scale(RecipeTreeTheme.NODE_HEIGHT);
        int itemCenterX = left + scaledSubtree / 2;
        int x = itemCenterX - itemCenterOffset(node);
        LayoutNode layoutNode = new LayoutNode(node, x, y, nodeW, nodeH, itemCenterX, scale(RecipeTreeTheme.ITEM_ICON_SIZE));
        layoutNodes.add(layoutNode);
        layoutNodeMap.put(node, layoutNode);
        List<TreeNode> children = visibleChildren(node);
        int childLeft = left + (scaledSubtree - scale(childrenWidth(children))) / 2;
        for (TreeNode child : children) {
            int childWidth = measure(child);
            layout(child, childLeft, y + scale(RecipeTreeTheme.LEVEL_GAP), childWidth);
            childLeft += scale(childWidth + RecipeTreeTheme.SIBLING_GAP);
        }
    }

    private int childrenWidth(List<TreeNode> children) {
        int width = 0;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                width += RecipeTreeTheme.SIBLING_GAP;
            }
            width += measure(children.get(i));
        }
        return width;
    }

    private int nodeWidth(TreeNode node) {
        int contentWidth = RecipeTreeTheme.ITEM_ICON_SIZE;
        if (node.recipe() != null) {
            contentWidth += RecipeTreeTheme.RECIPE_ICON_SIZE + RecipeTreeTheme.NODE_ICON_GAP;
        }
        return RecipeTreeTheme.NODE_PADDING * 2 + contentWidth;
    }

    private int itemCenterOffset(TreeNode node) {
        int itemLeft = scale(RecipeTreeTheme.NODE_PADDING);
        if (node.recipe() != null) {
            itemLeft += scale(RecipeTreeTheme.RECIPE_ICON_SIZE + RecipeTreeTheme.NODE_ICON_GAP);
        }
        return itemLeft + scale(RecipeTreeTheme.ITEM_ICON_SIZE) / 2;
    }

    private List<TreeNode> visibleChildren(TreeNode node) {
        return node.expanded() ? node.children() : List.of();
    }
}
