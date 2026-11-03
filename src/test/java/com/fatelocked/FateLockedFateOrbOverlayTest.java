package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Before;
import org.junit.Test;

/** The Fate orb shows the run's own Fate and Keys, and a "+N" that rises once and fades. */
public class FateLockedFateOrbOverlayTest
{
    private static final Gson GSON = new Gson();
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final DecisionService decisions = mock(DecisionService.class);
    private final FateLockedBundle bundle = mock(FateLockedBundle.class);
    private long now = 1_000_000_000L;
    private final FateLockedFateOrbOverlay overlay = new FateLockedFateOrbOverlay(plugin, config, () -> now);
    private final Graphics2D graphics = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB).createGraphics();

    @Before
    public void setUp()
    {
        when(config.fateOrb()).thenReturn(true);
        when(config.fateOrbGlow()).thenReturn(true);
        when(plugin.decisions()).thenReturn(decisions);
        when(plugin.getBundle()).thenReturn(bundle);
        when(decisions.trust()).thenReturn(Trust.TRUSTED);
    }

    private void fate(int points)
    {
        when(bundle.getState()).thenReturn(GSON.fromJson(
            "{\"keys\":2,\"fatePoints\":" + points + ",\"pityThreshold\":50}", FateLockedBundle.RunState.class));
    }

    @Test
    public void offOrForAnotherCharacterItDrawsNothing()
    {
        fate(10);
        when(config.fateOrb()).thenReturn(false);
        assertNull(overlay.render(graphics));
        when(config.fateOrb()).thenReturn(true);
        when(decisions.trust()).thenReturn(Trust.WRONG_CHARACTER);
        assertNull(overlay.render(graphics));
    }

    @Test
    public void itDrawsWithOrWithoutAPityThreshold()
    {
        fate(45);
        assertNotNull(overlay.render(graphics));
        when(bundle.getState()).thenReturn(GSON.fromJson("{\"keys\":0,\"fatePoints\":3}", FateLockedBundle.RunState.class));
        assertNotNull("an older tracker sends no threshold", overlay.render(graphics));
    }

    @Test
    public void moreFateRisesAsPlusNThenFades()
    {
        fate(10);
        overlay.render(graphics);
        assertEquals("the first frame is no gain", 0, overlay.risingGain());
        fate(12);
        overlay.render(graphics);
        assertEquals(2, overlay.risingGain());
        now += FateLockedFateOrbOverlay.GAIN_MILLIS * 1_000_000;
        overlay.render(graphics);
        assertEquals(0, overlay.risingGain());
    }

    @Test
    public void spendingFateRisesNothing()
    {
        fate(50);
        overlay.render(graphics);
        fate(0);
        overlay.render(graphics);
        assertEquals(0, overlay.risingGain());
    }
}
