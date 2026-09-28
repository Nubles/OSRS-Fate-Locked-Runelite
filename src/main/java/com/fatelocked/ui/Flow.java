package com.fatelocked.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Lays visible children left to right at their preferred sizes, wrapping to a new line
 * when the next one doesn't fit, and asks for the height the wrapped lines need. Before
 * the first layout it wraps to the width it was given.
 */
public final class Flow implements LayoutManager
{
    private final int hgap;
    private final int vgap;
    private final int widthHint;

    public Flow(int hgap, int vgap, int widthHint)
    {
        this.hgap = hgap;
        this.vgap = vgap;
        this.widthHint = widthHint;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        Insets insets = parent.getInsets();
        int available = (parent.getWidth() > 0 ? parent.getWidth() : widthHint) - insets.left - insets.right;
        int x = 0;
        int lineHeight = 0;
        int height = 0;
        int widest = 0;
        boolean first = true;
        for (Component child : parent.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }
            Dimension size = child.getPreferredSize();
            if (!first && x + hgap + size.width > available)
            {
                height += lineHeight + vgap;
                x = 0;
                lineHeight = 0;
                first = true;
            }
            x += (first ? 0 : hgap) + size.width;
            widest = Math.max(widest, x);
            lineHeight = Math.max(lineHeight, size.height);
            first = false;
        }
        height += lineHeight;
        return new Dimension(widest + insets.left + insets.right, height + insets.top + insets.bottom);
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
        int available = parent.getWidth() - insets.left - insets.right;
        int x = 0;
        int y = insets.top;
        int lineHeight = 0;
        boolean first = true;
        for (Component child : parent.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }
            Dimension size = child.getPreferredSize();
            if (!first && x + hgap + size.width > available)
            {
                y += lineHeight + vgap;
                x = 0;
                lineHeight = 0;
                first = true;
            }
            int left = x + (first ? 0 : hgap);
            child.setBounds(insets.left + left, y, Math.min(size.width, available), size.height);
            x = left + size.width;
            lineHeight = Math.max(lineHeight, size.height);
            first = false;
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
