package com.fatelocked.ui;

import com.fatelocked.rules.PermissionStatus;
import java.awt.BasicStroke;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.ColorScheme;

/**
 * Every colour the plugin draws with, in the sidebar and in game.
 *
 * <p>Surfaces and neutral text are RuneLite's own. Statuses come from a preset: the
 * default, one safe for colour-blind players (Okabe-Ito based), or the player's
 * custom colours. Colour never carries a status alone: text says it in a word, and
 * a locked edge is dashed over a dark underlay in every preset.
 */
public final class Palette
{
    public enum Preset
    {
        DEFAULT,
        COLOUR_BLIND_SAFE,
        CUSTOM
    }

    /** What a colour means, whatever the preset draws it with. */
    public enum Tone
    {
        /** Unlocked, can do, up to date. */
        GOOD,
        /** Not ready, waiting, may be out of date. */
        PENDING,
        /** Locked, failed, a different character. */
        BAD,
        /** Uncharted, needs checking, not connected. */
        NEUTRAL,
        /** Next to the unlocked area, in Chunked mode. */
        FRONTIER
    }

    /** The sidebar's background: RuneLite's own. */
    public static final Color PANEL = ColorScheme.DARK_GRAY_COLOR;
    /** A card on the sidebar. */
    public static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
    /** A header or row under the mouse, on a card: RuneLite's own card hover. */
    public static final Color HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
    /** A tile raised a little from its card. */
    public static final Color RAISED = new Color(38, 38, 38);
    /** A line between groups inside a card. */
    public static final Color HAIRLINE = new Color(50, 50, 50);
    /** A track behind a progress bar. */
    public static final Color TRACK = new Color(52, 52, 52);
    /** A secondary button. */
    public static final Color CONTROL = new Color(52, 52, 52);
    /** A secondary button under the mouse. */
    public static final Color CONTROL_HOVER = new Color(64, 64, 64);
    /** A switch that is off. */
    public static final Color SWITCH_OFF = new Color(76, 76, 76);
    /** Titles, in white as RuneLite's own panels have them. */
    public static final Color TITLE = Color.WHITE;
    /** Body text and values: RuneLite's own label colour. */
    public static final Color TEXT = ColorScheme.TEXT_COLOR;
    /** Labels and secondary lines. */
    public static final Color TEXT_MUTED = ColorScheme.LIGHT_GRAY_COLOR;
    /** The one accent: the web app's gold. */
    public static final Color ACCENT = new Color(251, 191, 36);
    /** Text drawn on the accent. */
    public static final Color ON_ACCENT = new Color(30, 30, 30);

    /** Beneath every locked edge, so it reads on any terrain and in any vision. */
    public static final Color UNDERLAY = new Color(0, 0, 0, 150);
    /** A chunk edge that isn't locked, in "all edges" mode. */
    public static final Color PLAIN_EDGE = new Color(255, 255, 255, 70);
    public static final BasicStroke LOCKED_EDGE_STROKE = new BasicStroke(3f, BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 5f}, 0f);
    public static final BasicStroke UNDERLAY_STROKE = new BasicStroke(5f, BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_ROUND);
    public static final BasicStroke PLAIN_EDGE_STROKE = new BasicStroke(1f);

    private static final int PILL_ALPHA = 40;

    private static final Palette DEFAULT_PALETTE = new Palette(
        texts(new Color(52, 211, 153), new Color(245, 158, 11), new Color(248, 113, 113),
            new Color(250, 204, 21)),
        new Color(60, 10, 10, 110), new Color(250, 204, 21, 130), new Color(248, 113, 113));

    private static final Palette COLOUR_BLIND_PALETTE = new Palette(
        texts(new Color(86, 180, 233), new Color(230, 159, 0), new Color(248, 113, 113),
            new Color(240, 228, 66)),
        new Color(20, 20, 40, 110), new Color(240, 228, 66, 120), new Color(248, 113, 113));

    private final Map<Tone, Color> text;
    private final Map<Tone, Color> pill = new EnumMap<>(Tone.class);
    private final Map<Tone, String> hex = new EnumMap<>(Tone.class);
    private final Color lockedShade;
    private final Color frontierFill;
    private final Color lockedEdge;

    private Palette(Map<Tone, Color> text, Color lockedShade, Color frontierFill, Color lockedEdge)
    {
        this.text = text;
        this.lockedShade = lockedShade;
        this.frontierFill = frontierFill;
        this.lockedEdge = lockedEdge;
        for (Map.Entry<Tone, Color> tone : text.entrySet())
        {
            pill.put(tone.getKey(), over(tone.getValue(), PILL_ALPHA, CARD));
            hex.put(tone.getKey(), String.format("%06x", tone.getValue().getRGB() & 0xFFFFFF));
        }
    }

    public static Palette defaults()
    {
        return DEFAULT_PALETTE;
    }

    /**
     * The palette for a preset. The custom colours apply only to {@link Preset#CUSTOM}:
     * unlocked colours unlocked text, frontier fills the frontier, and locked colours
     * locked text, edges and the locked shade, which keeps its alpha.
     */
    public static Palette of(Preset preset, Color unlocked, Color frontier, Color locked)
    {
        switch (preset)
        {
            case COLOUR_BLIND_SAFE:
                return COLOUR_BLIND_PALETTE;
            case CUSTOM:
                Color lockedText = opaque(locked);
                return new Palette(texts(opaque(unlocked), DEFAULT_PALETTE.text(Tone.PENDING), lockedText,
                    opaque(frontier)), locked, frontier, lockedText);
            default:
                return DEFAULT_PALETTE;
        }
    }

    /** The tone a rules decision is drawn in. */
    public static Tone tone(PermissionStatus status)
    {
        switch (status)
        {
            case ALLOWED:
                return Tone.GOOD;
            case NOT_READY:
                return Tone.PENDING;
            case LOCKED:
                return Tone.BAD;
            default:
                return Tone.NEUTRAL;
        }
    }

    /** Text in a tone, on the panel or a card. */
    public Color text(Tone tone)
    {
        return text.get(tone);
    }

    /** The opaque fill behind a status pill's word, on a card. */
    public Color pill(Tone tone)
    {
        return pill.get(tone);
    }

    /** A tone as {@code rrggbb} for game text's {@code <col=>} tags. */
    public String hex(Tone tone)
    {
        return hex.get(tone);
    }

    /** Locked land on the world map and the minimap: darker, like fog. */
    public Color lockedShade()
    {
        return lockedShade;
    }

    /** The frontier on the world map, in Chunked mode. */
    public Color frontierFill()
    {
        return frontierFill;
    }

    /** A locked edge in the scene and on the minimap, drawn dashed over the underlay. */
    public Color lockedEdge()
    {
        return lockedEdge;
    }

    /** The fade when a locked area is entered. */
    public Color flash()
    {
        return lockedEdge;
    }

    private static Map<Tone, Color> texts(Color good, Color pending, Color bad, Color frontier)
    {
        Map<Tone, Color> text = new EnumMap<>(Tone.class);
        text.put(Tone.GOOD, good);
        text.put(Tone.PENDING, pending);
        text.put(Tone.BAD, bad);
        text.put(Tone.NEUTRAL, TEXT_MUTED);
        text.put(Tone.FRONTIER, frontier);
        return text;
    }

    private static Color opaque(Color colour)
    {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    /** {@code top} at {@code alpha} over an opaque {@code bottom}, as Java2D composites it. */
    static Color over(Color top, int alpha, Color bottom)
    {
        float a = alpha / 255f;
        return new Color(
            Math.round(a * top.getRed() + (1 - a) * bottom.getRed()),
            Math.round(a * top.getGreen() + (1 - a) * bottom.getGreen()),
            Math.round(a * top.getBlue() + (1 - a) * bottom.getBlue()));
    }
}
