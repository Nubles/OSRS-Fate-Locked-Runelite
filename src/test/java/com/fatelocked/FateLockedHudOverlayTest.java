package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.ui.Palette;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import org.junit.Before;
import org.junit.Test;

/**
 * E6: the HUD draws the plugin's model on RuneLite's own panel, labels plain and values in the
 * palette's tones, and builds the panel again only when the model or the palette changes.
 */
public class FateLockedHudOverlayTest
{
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedHudOverlay overlay = new FateLockedHudOverlay(plugin);
    private final Palette palette = Palette.defaults();

    private static final HudModel COMPACT = new HudModel(Arrays.asList(
        new HudModel.Line("Here", "Lumbridge", null),
        new HudModel.Line("Status", "Locked", Palette.Tone.BAD),
        HudModel.Line.heading("Quests"),
        new HudModel.Line("+2 more", null, Palette.Tone.NEUTRAL)), false);

    @Before
    public void setUp()
    {
        when(plugin.palette()).thenReturn(palette);
        when(plugin.hudModel()).thenReturn(COMPACT);
    }

    @Test
    public void theModelsLinesAreDrawnInThePalettesColours() throws Exception
    {
        render();

        List<LayoutableRenderableEntity> children = overlay.getPanelComponent().getChildren();
        assertEquals("the title and one per line", 5, children.size());
        TitleComponent title = (TitleComponent) children.get(0);
        assertEquals("Fate Locked", field(title, "text"));
        assertEquals(Palette.ACCENT, field(title, "color"));

        assertLine(children.get(1), "Here", Palette.TITLE, "Lumbridge", Palette.TITLE);
        assertLine(children.get(2), "Status", Palette.TITLE, "Locked", palette.text(Palette.Tone.BAD));
        assertLine(children.get(3), "Quests", Palette.ACCENT, null, null);
        assertLine(children.get(4), "+2 more", palette.text(Palette.Tone.NEUTRAL), null, null);
        assertEquals(165, overlay.getPanelComponent().getPreferredSize().width);
    }

    @Test
    public void detailedIsWiderForWhatThePlaceHolds()
    {
        when(plugin.hudModel()).thenReturn(new HudModel(COMPACT.getLines(), true));
        render();
        assertEquals(210, overlay.getPanelComponent().getPreferredSize().width);
    }

    @Test
    public void noHudDrawsNothing()
    {
        when(plugin.hudModel()).thenReturn(HudModel.NONE);
        Graphics2D graphics = mock(Graphics2D.class);
        assertNull(overlay.render(graphics));
        verifyNoInteractions(graphics);
        assertTrue(overlay.getPanelComponent().getChildren().isEmpty());
    }

    @Test
    public void thePanelIsBuiltAgainOnlyWhenTheModelOrPaletteChanges() throws Exception
    {
        render();
        Object status = overlay.getPanelComponent().getChildren().get(2);
        render();
        assertSame("nothing changed: the same panel", status, overlay.getPanelComponent().getChildren().get(2));

        Palette custom = Palette.of(Palette.Preset.CUSTOM, new Color(0, 128, 255, 110),
            new Color(255, 255, 0, 100), new Color(128, 0, 128, 110));
        when(plugin.palette()).thenReturn(custom);
        render();
        Object recoloured = overlay.getPanelComponent().getChildren().get(2);
        assertNotSame(status, recoloured);
        assertEquals(custom.text(Palette.Tone.BAD), field(recoloured, "rightColor"));

        when(plugin.hudModel()).thenReturn(new HudModel(Arrays.asList(
            new HudModel.Line("Here", "Draynor Village", null)), false));
        render();
        assertEquals("a new model: a new panel", 2, overlay.getPanelComponent().getChildren().size());
        assertEquals("Draynor Village", field(overlay.getPanelComponent().getChildren().get(1), "right"));
    }

    private void render()
    {
        BufferedImage image = new BufferedImage(400, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.render(graphics);
        graphics.dispose();
    }

    private static void assertLine(Object child, String left, Color leftColour, String right, Color rightColour)
        throws Exception
    {
        LineComponent line = (LineComponent) child;
        assertEquals(left, field(line, "left"));
        assertEquals(left, leftColour, field(line, "leftColor"));
        assertEquals(left, right, field(line, "right"));
        if (right != null)
        {
            assertEquals(left, rightColour, field(line, "rightColor"));
        }
    }

    private static Object field(Object component, String name) throws Exception
    {
        Field field = component.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(component);
    }
}
