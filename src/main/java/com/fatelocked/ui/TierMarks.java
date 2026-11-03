package com.fatelocked.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * A tier as the tracker's OSRS layout marks it (Settings > Tier marks, numerals): the Roman
 * numeral I to X in the colour of the cut gem that stands for it, Opal to Zenyte. The game
 * tabs' tier marks (skills and items) draw them the same way.
 */
public final class TierMarks
{
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** The game's orange, for a skill levelled past what its tier opens. */
    public static final Color PAST_TIER = Palette.TIER_PAST;
    /** The game's green and red, for gear the slot's tier allows or doesn't. */
    public static final Color ALLOWED = Palette.TIER_ALLOWED;
    public static final Color ABOVE = Palette.TIER_ABOVE;

    private TierMarks()
    {
    }

    /** I to X; tiers past ten read X. */
    public static String roman(int tier)
    {
        return ROMAN[Math.max(1, Math.min(tier, ROMAN.length)) - 1];
    }

    /** The colour of the gem that stands for this tier. */
    public static Color gem(int tier)
    {
        return Palette.TIER_GEMS[Math.max(1, Math.min(tier, Palette.TIER_GEMS.length)) - 1];
    }

    /** The highest level a skill tier opens: ten per tier, and every level at tier X. */
    public static int levelCap(int tier)
    {
        return tier >= 10 ? 99 : tier * 10;
    }

    /** Text with the game's one-pixel black shadow, its baseline at y. */
    public static void shadowed(Graphics2D graphics, String text, int x, int y, Font font, Color colour)
    {
        graphics.setFont(font);
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(colour);
        graphics.drawString(text, x, y);
    }

    /** The width of text in a font. */
    public static int width(Graphics2D graphics, String text, Font font)
    {
        FontMetrics metrics = graphics.getFontMetrics(font);
        return metrics.stringWidth(text);
    }
}
