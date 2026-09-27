package com.fatelocked.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * A button drawn in the sidebar's own style. A card has at most one primary button,
 * in the accent; other actions are secondary, or a quiet link.
 */
public class FlatButton extends JButton
{
    public enum Kind
    {
        PRIMARY,
        SECONDARY,
        LINK
    }

    private static final int BUTTON_HEIGHT = 24;
    private static final int PAD_X = 10;

    private final Kind kind;

    public FlatButton(String text, Kind kind)
    {
        super(text);
        this.kind = kind;
        setFont(kind == Kind.PRIMARY ? Type.title() : Type.body());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public Kind getKind()
    {
        return kind;
    }

    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = Draw.width(this, metrics, getText());
        return kind == Kind.LINK
            ? new Dimension(width, metrics.getHeight())
            : new Dimension(width + 2 * PAD_X, BUTTON_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            ButtonModel model = getModel();
            boolean hover = model.isRollover() && isEnabled();
            boolean pressed = model.isArmed() && model.isPressed();
            FontMetrics metrics = getFontMetrics(getFont());
            int textWidth = Draw.width(this, metrics, getText());
            int x = (getWidth() - textWidth) / 2;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setFont(getFont());

            if (kind == Kind.LINK)
            {
                g2.setColor(isEnabled() ? Palette.ACCENT : Palette.TEXT_MUTED);
                BasicGraphicsUtils.drawString(this, g2, getText(), 0, y);
                if (hover)
                {
                    g2.fillRect(0, y + 1, textWidth, 1);
                }
                return;
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill;
            Color text;
            if (kind == Kind.PRIMARY)
            {
                fill = !isEnabled() ? Palette.CONTROL : pressed ? Palette.ACCENT.darker() : hover ? Palette.ACCENT.brighter() : Palette.ACCENT;
                text = isEnabled() ? Palette.ON_ACCENT : Palette.TEXT_MUTED;
            }
            else
            {
                fill = pressed ? Palette.CARD : hover ? Palette.CONTROL_HOVER : Palette.CONTROL;
                text = isEnabled() ? Palette.TEXT : Palette.TEXT_MUTED;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Card.ARC, Card.ARC);
            g2.setColor(text);
            BasicGraphicsUtils.drawString(this, g2, getText(), x, y);
        }
        finally
        {
            g2.dispose();
        }
    }
}
