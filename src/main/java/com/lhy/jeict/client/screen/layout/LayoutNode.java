package com.lhy.jeict.client.screen.layout;

import com.lhy.jeict.tree.TreeNode;

public record LayoutNode(TreeNode node, int x, int y, int width, int height, int itemCenterX, int itemSize) {
    public int centerX() {
        return x + width / 2;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean itemContains(int mouseX, int mouseY) {
        int left = itemCenterX - itemSize / 2;
        int top = y + (height - itemSize) / 2;
        return mouseX >= left && mouseX < left + itemSize && mouseY >= top && mouseY < top + itemSize;
    }
}
