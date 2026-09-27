package com.fatelocked.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JToggleButton;

/** An on/off switch: the accent when on, grey when off. */
public class ToggleSwitch extends JToggleButton
{
    private static final int TRACK_WIDTH = 28;
    private static final int TRACK_HEIGHT = 16;
    private static final int KNOB = 12;

    public ToggleSwitch()
    {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(TRACK_WIDTH, TRACK_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize()
    {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected();
            boolean hover = getModel().isRollover() && isEnabled();
            int y = (getHeight() - TRACK_HEIGHT) / 2;
            Color track = !isEnabled() ? Palette.SWITCH_OFF : on ? (hover ? Palette.ACCENT.brighter() : Palette.ACCENT)
                : hover ? Palette.SWITCH_OFF.brighter() : Palette.SWITCH_OFF;
            g2.setColor(track);
            g2.fillRoundRect(0, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            int inset = (TRACK_HEIGHT - KNOB) / 2;
            int knobX = on ? TRACK_WIDTH - inset - KNOB : inset;
            g2.setColor(on ? Palette.ON_ACCENT : Palette.TEXT);
            g2.fillOval(knobX, y + inset, KNOB, KNOB);
        }
        finally
        {
            g2.dispose();
        }
    }
}
