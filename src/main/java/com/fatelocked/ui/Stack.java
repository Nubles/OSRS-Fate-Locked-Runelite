package com.fatelocked.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Stacks a container's visible children top to bottom, each at the container's full
 * width and its own preferred height, with a gap between them. A hidden child takes
 * no space and no gap, unlike RuneLite's grid layouts.
 */
public final class Stack implements LayoutManager
{
    private final int gap;

    public Stack(int gap)
    {
        this.gap = gap;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        Insets insets = parent.getInsets();
        int width = 0;
        int height = 0;
        int shown = 0;
        for (Component child : parent.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }
            Dimension size = child.getPreferredSize();
            width = Math.max(width, size.width);
            height += size.height;
            shown++;
        }
        height += Math.max(0, shown - 1) * gap;
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent)
    {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent)
    {
        Insets insets = parent.getInsets();
        int width = parent.getWidth() - insets.left - insets.right;
        int y = insets.top;
        for (Component child : parent.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }
            int height = child.getPreferredSize().height;
            child.setBounds(insets.left, y, width, height);
            y += height + gap;
        }
    }

    @Override
    public void addLayoutComponent(String name, Component component)
    {
    }

    @Override
    public void removeLayoutComponent(Component component)
    {
    }
}
