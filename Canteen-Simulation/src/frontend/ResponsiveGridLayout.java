package frontend;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

class ResponsiveGridLayout implements LayoutManager {
    private final int minCellWidth;
    private final int hgap;
    private final int vgap;

    ResponsiveGridLayout(int minCellWidth, int hgap, int vgap) {
        this.minCellWidth = Math.max(1, minCellWidth);
        this.hgap = Math.max(0, hgap);
        this.vgap = Math.max(0, vgap);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return layoutSize(parent, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return layoutSize(parent, false);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int count = parent.getComponentCount();
        if (count == 0) {
            return;
        }

        int availableWidth = Math.max(1, parent.getWidth() - insets.left - insets.right);
        int columns = columnsFor(availableWidth, count);
        int rows = (count + columns - 1) / columns;
        int cellWidth = Math.max(1, (availableWidth - (columns - 1) * hgap) / columns);
        int availableHeight = Math.max(1, parent.getHeight() - insets.top - insets.bottom);
        int cellHeight = Math.max(1, (availableHeight - (rows - 1) * vgap) / rows);

        for (int i = 0; i < count; i++) {
            Component child = parent.getComponent(i);
            int row = i / columns;
            int col = i % columns;
            int x = insets.left + col * (cellWidth + hgap);
            int y = insets.top + row * (cellHeight + vgap);
            child.setBounds(x, y, cellWidth, cellHeight);
        }
    }

    private Dimension layoutSize(Container parent, boolean preferred) {
        Insets insets = parent.getInsets();
        int count = parent.getComponentCount();
        if (count == 0) {
            return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
        }

        int width = 0;
        int height = 0;
        for (int i = 0; i < count; i++) {
            Dimension size = preferred
                    ? parent.getComponent(i).getPreferredSize()
                    : parent.getComponent(i).getMinimumSize();
            width = Math.max(width, size.width);
            height = Math.max(height, size.height);
        }
        int columns = Math.min(count, Math.max(1, 1200 / minCellWidth));
        int rows = (count + columns - 1) / columns;
        return new Dimension(
                insets.left + insets.right + columns * width + (columns - 1) * hgap,
                insets.top + insets.bottom + rows * height + (rows - 1) * vgap
        );
    }

    private int columnsFor(int width, int count) {
        int columns = Math.max(1, (width + hgap) / (minCellWidth + hgap));
        return Math.min(count, columns);
    }
}
