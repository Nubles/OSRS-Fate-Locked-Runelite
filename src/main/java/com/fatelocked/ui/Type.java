package com.fatelocked.ui;

import java.awt.Font;
import net.runelite.client.ui.FontManager;

/**
 * The three typefaces the plugin draws text with: RuneLite's own RuneScape fonts,
 * at the one size they are drawn for. They are never derived to another size, which
 * would blur them; hierarchy comes from weight, colour and space instead.
 */
public final class Type
{
    private Type()
    {
    }

    /** Titles, and the value that matters most in a row. */
    public static Font title()
    {
        return FontManager.getRunescapeBoldFont();
    }

    /** Body text and labels. */
    public static Font body()
    {
        return FontManager.getRunescapeFont();
    }

    /** Secondary lines: reasons, times and counts. */
    public static Font small()
    {
        return FontManager.getRunescapeSmallFont();
    }
}
