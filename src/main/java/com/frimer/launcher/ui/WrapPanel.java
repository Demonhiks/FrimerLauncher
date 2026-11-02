package com.frimer.launcher.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Панель с «обтекаемой» раскладкой: элементы переносятся на новую строку
 * по ширине видимой области. Нужна и плиткам сборок, и сетке скриншотов.
 */
public final class WrapPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    private final int gap;

    public WrapPanel(int gap) {
        super(new WrapLayout(gap));
        this.gap = gap;
    }

    public int gap() {
        return gap;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation,
                                          int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation,
                                           int direction) {
        return Math.max(60, visibleRect.height - 40);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /** Раскладка «строками с переносом». */
    private static final class WrapLayout implements LayoutManager {

        private final int gap;

        WrapLayout(int gap) {
            this.gap = gap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int limit = availableWidth(target);
                if (limit <= 0) {
                    limit = 640;
                }
                Insets insets = target.getInsets();
                int usable = Math.max(120, limit - insets.left - insets.right - gap);
                int usedWidth = 0;
                int rowWidth = 0;
                int rowHeight = 0;
                int totalHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = c.getPreferredSize();
                    if (rowWidth > 0 && rowWidth + gap + d.width > usable) {
                        totalHeight += rowHeight + gap;
                        usedWidth = Math.max(usedWidth, rowWidth);
                        rowWidth = d.width;
                        rowHeight = d.height;
                    } else {
                        rowWidth = rowWidth == 0 ? d.width : rowWidth + gap + d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                totalHeight += rowHeight;
                usedWidth = Math.max(usedWidth, rowWidth);
                return new Dimension(usedWidth + insets.left + insets.right + gap,
                        totalHeight + insets.top + insets.bottom + gap);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return new Dimension(120, 60);
        }

        @Override
        public void layoutContainer(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int limit = availableWidth(target) - insets.right;
                int x = insets.left;
                int y = insets.top;
                int rowHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = c.getPreferredSize();
                    if (x > insets.left && x + d.width > limit) {
                        x = insets.left;
                        y += rowHeight + gap;
                        rowHeight = 0;
                    }
                    c.setBounds(x, y, Math.min(d.width, Math.max(120, limit - x)), d.height);
                    x += d.width + gap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
        }

        /** Ширина, доступная элементам: у вьюпорта она точнее, чем у самой панели. */
        private int availableWidth(Container target) {
            Container parent = target.getParent();
            if (parent instanceof javax.swing.JViewport viewport) {
                int extent = viewport.getExtentSize().width;
                if (extent > 0) {
                    return extent;
                }
            }
            return target.getWidth();
        }
    }
}
