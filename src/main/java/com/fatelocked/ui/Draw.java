package com.fatelocked.ui;

import java.awt.FontMetrics;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/** Text measured the way Swing draws it for a component. */
final class Draw
{
    private Draw()
    {
    }

    /** The width of {@code text} in whole pixels, as {@code c} draws it. */
    static int width(JComponent c, FontMetrics metrics, String text)
    {
        return (int) Math.ceil(BasicGraphicsUtils.getStringWidth(c, metrics, text));
    }
}
