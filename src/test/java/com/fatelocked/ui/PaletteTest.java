package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.ui.ColourMaths.Vision;
import com.fatelocked.ui.Palette.Preset;
import com.fatelocked.ui.Palette.Tone;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PaletteTest
{
    private static final List<Palette> BUILT_IN = Arrays.asList(
        Palette.defaults(),
        Palette.of(Preset.COLOUR_BLIND_SAFE, Color.BLACK, Color.BLACK, Color.BLACK));

    /** Grass and sand in the scene, green and tan on the world map: approximations, not samples. */
    private static final List<Color> TERRAIN = Arrays.asList(
        new Color(78, 106, 43), new Color(196, 167, 111), new Color(82, 100, 55), new Color(143, 126, 89));

    @Test
    public void everyWordReadsOnRuneLitesGreys()
    {
        for (Palette palette : BUILT_IN)
        {
            for (Tone tone : Tone.values())
            {
                assertReadable(tone.name(), palette.text(tone), Palette.PANEL);
                assertReadable(tone.name(), palette.text(tone), Palette.CARD);
                assertReadable(tone.name() + " on its pill", palette.text(tone), palette.pill(tone));
            }
        }
        for (Color neutral : Arrays.asList(Palette.TITLE, Palette.TEXT, Palette.TEXT_MUTED, Palette.ACCENT))
        {
            assertReadable("neutral", neutral, Palette.PANEL);
            assertReadable("neutral", neutral, Palette.CARD);
        }
        assertReadable("a word on the accent", Palette.ON_ACCENT, Palette.ACCENT);
    }

    @Test
    public void theColourBlindPresetKeepsEveryStatusApart()
    {
        Palette safe = Palette.of(Preset.COLOUR_BLIND_SAFE, Color.BLACK, Color.BLACK, Color.BLACK);
        for (Vision vision : Arrays.asList(Vision.PROTAN, Vision.DEUTAN))
        {
            double closest = closestPair(safe, vision);
            assertTrue(vision + " sees two statuses " + closest + " apart", closest >= 10);
        }
    }

    @Test
    public void theDefaultPresetStaysAsDistinctAsItIsToday()
    {
        // Not claimed safe for colour blindness, but no status may drift closer than this.
        for (Vision vision : Vision.values())
        {
            double closest = closestPair(Palette.defaults(), vision);
            assertTrue(vision + " sees two statuses " + closest + " apart", closest >= 7);
        }
    }

    @Test
    public void lockedLandStandsOutFromTheTerrainUnderIt()
    {
        for (Palette palette : BUILT_IN)
        {
            for (Color ground : TERRAIN)
            {
                Color shaded = ColourMaths.over(palette.lockedShade(), ground);
                for (Vision vision : Arrays.asList(Vision.NORMAL, Vision.PROTAN, Vision.DEUTAN))
                {
                    double apart = ColourMaths.difference(ground, shaded, vision);
                    assertTrue(vision + " over " + ground + ": " + apart, apart >= 10);
                }
            }
        }
    }

    @Test
    public void aLockedEdgeIsDashedOverAWiderUnderlay()
    {
        assertNotNull(Palette.LOCKED_EDGE_STROKE.getDashArray());
        assertTrue(Palette.UNDERLAY_STROKE.getLineWidth() > Palette.LOCKED_EDGE_STROKE.getLineWidth());
        // The game view's and minimap's dashes are cut on the ground, each drawn whole, as wide as the world map's.
        assertNull(Palette.LOCKED_DASH_STROKE.getDashArray());
        assertEquals(Palette.LOCKED_EDGE_STROKE.getLineWidth(), Palette.LOCKED_DASH_STROKE.getLineWidth(), 0);
        assertTrue(Palette.UNDERLAY.getAlpha() >= 128);
        for (Palette palette : BUILT_IN)
        {
            assertEquals(255, palette.lockedEdge().getAlpha());
        }
    }

    @Test
    public void customColoursApplyOnlyToTheCustomPreset()
    {
        Color unlocked = new Color(1, 2, 3, 40);
        Color frontier = new Color(4, 5, 6, 50);
        Color locked = new Color(7, 8, 9, 60);

        Palette custom = Palette.of(Preset.CUSTOM, unlocked, frontier, locked);
        assertEquals(new Color(1, 2, 3), custom.text(Tone.GOOD));
        assertEquals(new Color(7, 8, 9), custom.text(Tone.BAD));
        assertEquals(new Color(4, 5, 6), custom.text(Tone.FRONTIER));
        assertEquals(frontier, custom.frontierFill());
        assertEquals("the locked shade keeps its alpha", locked, custom.lockedShade());
        assertEquals(new Color(7, 8, 9), custom.lockedEdge());
        assertEquals(Palette.defaults().text(Tone.PENDING), custom.text(Tone.PENDING));

        assertSame(Palette.defaults(), Palette.of(Preset.DEFAULT, unlocked, frontier, locked));
    }

    @Test
    public void gameTextTakesTheSameColourAsTheSidebar()
    {
        Palette palette = Palette.defaults();
        assertEquals("f87171", palette.hex(Tone.BAD));
        for (Tone tone : Tone.values())
        {
            assertEquals(palette.text(tone).getRGB() & 0xFFFFFF, Integer.parseInt(palette.hex(tone), 16));
        }
    }

    @Test
    public void aDecisionTakesTheToneOfItsStatus()
    {
        assertEquals(Tone.GOOD, Palette.tone(PermissionStatus.ALLOWED));
        assertEquals(Tone.PENDING, Palette.tone(PermissionStatus.NOT_READY));
        assertEquals(Tone.BAD, Palette.tone(PermissionStatus.LOCKED));
        assertEquals(Tone.NEUTRAL, Palette.tone(PermissionStatus.UNKNOWN));
    }

    private static double closestPair(Palette palette, Vision vision)
    {
        double closest = Double.MAX_VALUE;
        Tone[] tones = Tone.values();
        for (int i = 0; i < tones.length; i++)
        {
            for (int j = i + 1; j < tones.length; j++)
            {
                closest = Math.min(closest,
                    ColourMaths.difference(palette.text(tones[i]), palette.text(tones[j]), vision));
            }
        }
        return closest;
    }

    private static void assertReadable(String what, Color text, Color background)
    {
        double ratio = ColourMaths.contrast(text, background);
        assertTrue(what + " " + text + " is " + ratio + ":1 on " + background, ratio >= 4.5);
    }
}
