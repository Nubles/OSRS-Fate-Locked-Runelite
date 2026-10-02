package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Palette.Tone;
import java.awt.Color;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;

/** U15, E1: the colour settings choose one palette, for the sidebar and everything drawn in game. */
public class PaletteSettingsTest
{
    private static final Color PURPLE = new Color(128, 0, 128, 110);

    private final FateLockedConfig config = mock(FateLockedConfig.class);

    @Before
    public void setUp()
    {
        when(config.unlockedColor()).thenReturn(new Color(0, 128, 255, 110));
        when(config.frontierColor()).thenReturn(new Color(255, 255, 0, 100));
        when(config.lockedColor()).thenReturn(PURPLE);
    }

    @Test
    public void eachPresetIsThePalettesOwnAndTheCustomColoursCountOnlyUnderCustom()
    {
        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.DEFAULT);
        assertSame(Palette.defaults(), FateLockedPlugin.palette(config));

        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.COLOUR_BLIND_SAFE);
        Palette safe = FateLockedPlugin.palette(config);
        assertSame(Palette.of(Palette.Preset.COLOUR_BLIND_SAFE, null, null, null), safe);
        assertNotEquals(Palette.defaults().text(Tone.GOOD), safe.text(Tone.GOOD));

        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.CUSTOM);
        Palette custom = FateLockedPlugin.palette(config);
        assertEquals(new Color(128, 0, 128), custom.text(Tone.BAD));
        assertEquals(PURPLE, custom.lockedShade());
        // The Unlocked colour colours words and labels only, its transparency dropped, as its
        // description says (accuracy review, P-5).
        assertEquals(new Color(0, 128, 255), custom.text(Tone.GOOD));
    }

    /** A colour setting changes the palette at once and hands it to the sidebar; other settings don't. */
    @Test
    public void aColourSettingRedrawsInItsPalette() throws Exception
    {
        FateLockedPlugin plugin = new FateLockedPlugin();
        FateLockedPanel panel = mock(FateLockedPanel.class);
        set(plugin, "config", config);
        set(plugin, "panel", panel);
        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.COLOUR_BLIND_SAFE);

        GearDecisionTest.configChanged(plugin, "useNotifier");
        assertSame(Palette.defaults(), plugin.palette());
        verify(panel, never()).setPalette(any());

        for (String key : new String[] {"colourPreset", "unlockedColor", "frontierColor", "lockedColor"})
        {
            set(plugin, "palette", Palette.defaults());
            GearDecisionTest.configChanged(plugin, key);
            assertSame(key, FateLockedPlugin.palette(config), plugin.palette());
        }
        verify(panel).setPalette(FateLockedPlugin.palette(config));
    }

    private static void set(FateLockedPlugin plugin, String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
