package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.FateLockedInfoBox.Kind;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Art;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * A15, E7: three infoboxes, each with its own pinned name, the sidebar's OSRS art and the web
 * app's words, counting only for the rules' own character.
 */
public class FateLockedInfoBoxTest
{
    private static final Gson GSON = new Gson();
    private static final BufferedImage ART = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);

    @Before
    public void setUp()
    {
        when(plugin.palette()).thenReturn(Palette.defaults());
    }

    /** RuneLite saves each box's place under its name, so the names never change. */
    @Test
    public void eachBoxHasItsOwnPinnedName()
    {
        assertEquals("FateLocked_Keys", new FateLockedInfoBox(Kind.KEYS, plugin).getName());
        assertEquals("FateLocked_FatePoints", new FateLockedInfoBox(Kind.FATE_POINTS, plugin).getName());
        assertEquals("FateLocked_Unlocked", new FateLockedInfoBox(Kind.UNLOCKED, plugin).getName());
    }

    @Test
    public void theArtIsTheSidebars()
    {
        assertSame(Art.KEYS, Kind.KEYS.art());
        assertSame(Art.FATE_POINTS, Kind.FATE_POINTS.art());
        assertSame("the world map's globe", Art.PLACE, Kind.UNLOCKED.art());
    }

    @Test
    public void theCountsAreTheRunsInTheWebsWords()
    {
        FateLockedBundle.RunState run = run(3, 1, 0, 12);
        assertEquals("3", FateLockedInfoBox.text(Kind.KEYS, run, null));
        assertEquals("Keys: 3</br>Omni-Keys: 1", FateLockedInfoBox.tooltip(Kind.KEYS, run, null));
        assertEquals("Keys: 3</br>Chaos Keys: 2", FateLockedInfoBox.tooltip(Kind.KEYS, run(3, 0, 2, 12), null));
        assertEquals("Keys: 0", FateLockedInfoBox.tooltip(Kind.KEYS, run(0, 0, 0, 0), null));
        assertEquals("12", FateLockedInfoBox.text(Kind.FATE_POINTS, run, null));
        assertEquals("Fate Points: 12", FateLockedInfoBox.tooltip(Kind.FATE_POINTS, run, null));

        Progress progress = new Progress(Progress.AREAS, 15, 187, 45, 624);
        assertEquals(progress.percent() + "%", FateLockedInfoBox.text(Kind.UNLOCKED, run, progress));
        assertEquals("15 of 187 areas unlocked</br>45 of 624 chunks",
            FateLockedInfoBox.tooltip(Kind.UNLOCKED, run, progress));
    }

    @Test
    public void aBoxWithNothingToCountIsNotDrawn()
    {
        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.empty(), null, null));
        when(plugin.getBundle()).thenReturn(FateLockedBundle.empty());
        for (Kind kind : Kind.values())
        {
            FateLockedInfoBox box = drawnWithArt(kind);
            assertNull(kind.name(), box.getText());
            assertNull(kind.name(), box.getTooltip());
            assertFalse(kind.name(), box.render());
        }
    }

    /** The HUD says only that the run isn't theirs; the boxes say nothing. */
    @Test
    public void anotherCharacterSeesNoCounts() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        when(plugin.getBundle()).thenReturn(mid);
        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else"));
        for (Kind kind : Kind.values())
        {
            assertFalse(kind.name(), drawnWithArt(kind).render());
        }

        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example"));
        for (Kind kind : Kind.values())
        {
            assertTrue(kind.name(), drawnWithArt(kind).render());
            assertFalse("not before its art arrives", new FateLockedInfoBox(kind, plugin).render());
        }
        assertEquals(String.valueOf(mid.getState().getKeys()), drawnWithArt(Kind.KEYS).getText());
        assertEquals(Palette.TITLE, drawnWithArt(Kind.FATE_POINTS).getTextColor());
    }

    @Test
    public void coloursComeFromThePalette()
    {
        Palette custom = Palette.of(Palette.Preset.CUSTOM, new Color(0, 128, 255, 110),
            new Color(255, 255, 0, 100), new Color(128, 0, 128, 110));
        for (Palette palette : new Palette[] {Palette.defaults(), custom})
        {
            when(plugin.palette()).thenReturn(palette);
            assertEquals(palette.text(Palette.Tone.FRONTIER), new FateLockedInfoBox(Kind.KEYS, plugin).getTextColor());
            assertEquals(Palette.TITLE, new FateLockedInfoBox(Kind.FATE_POINTS, plugin).getTextColor());
            assertEquals(palette.text(Palette.Tone.GOOD), new FateLockedInfoBox(Kind.UNLOCKED, plugin).getTextColor());
        }
        assertNotEquals(Palette.defaults().text(Palette.Tone.GOOD), custom.text(Palette.Tone.GOOD));
    }

    /** The setting adds the three boxes: item art at once, the globe once the cache hands it over. */
    @Test
    public void theSettingAddsTheBoxesWithTheirArt() throws Exception
    {
        FateLockedPlugin real = new FateLockedPlugin();
        FateLockedConfig config = mock(FateLockedConfig.class);
        InfoBoxManager boxes = mock(InfoBoxManager.class);
        ItemManager items = mock(ItemManager.class);
        SpriteManager sprites = mock(SpriteManager.class);
        AsyncBufferedImage key = mock(AsyncBufferedImage.class);
        AsyncBufferedImage stardust = mock(AsyncBufferedImage.class);
        when(items.getImage(Art.KEYS.id(), Art.KEYS.detail(), false)).thenReturn(key);
        when(items.getImage(Art.FATE_POINTS.id(), Art.FATE_POINTS.detail(), false)).thenReturn(stardust);
        PluginTestSupport.set(real, "config", config);
        PluginTestSupport.set(real, "infoBoxManager", boxes);
        PluginTestSupport.set(real, "itemManager", items);
        PluginTestSupport.set(real, "spriteManager", sprites);
        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshInfoBoxes");
        refresh.setAccessible(true);

        refresh.invoke(real);
        verify(boxes, never()).addInfoBox(any());

        when(config.showInfoBoxes()).thenReturn(true);
        refresh.invoke(real);
        ArgumentCaptor<InfoBox> added = ArgumentCaptor.forClass(InfoBox.class);
        verify(boxes, times(3)).addInfoBox(added.capture());
        List<InfoBox> shown = added.getAllValues();
        assertEquals(Kind.KEYS, ((FateLockedInfoBox) shown.get(0)).kind());
        assertSame(key, shown.get(0).getImage());
        assertEquals(Kind.FATE_POINTS, ((FateLockedInfoBox) shown.get(1)).kind());
        assertSame(stardust, shown.get(1).getImage());

        FateLockedInfoBox unlocked = (FateLockedInfoBox) shown.get(2);
        assertEquals(Kind.UNLOCKED, unlocked.kind());
        assertNull("the globe comes from the cache", unlocked.getImage());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<BufferedImage>> globe = ArgumentCaptor.forClass(Consumer.class);
        verify(sprites).getSpriteAsync(eq(Art.PLACE.id()), eq(Art.PLACE.detail()), globe.capture());
        globe.getValue().accept(ART);
        assertSame(ART, unlocked.getImage());
        verify(boxes).updateInfoBoxImage(unlocked);
        // Each refresh takes the old boxes away first.
        verify(boxes, times(2)).removeIf(any());
    }

    private FateLockedInfoBox drawnWithArt(Kind kind)
    {
        FateLockedInfoBox box = new FateLockedInfoBox(kind, plugin);
        box.setImage(ART);
        return box;
    }

    private static FateLockedBundle.RunState run(int keys, int omni, int chaos, int fatePoints)
    {
        return GSON.fromJson("{\"keys\":" + keys + ",\"specialKeys\":" + omni + ",\"chaosKeys\":" + chaos
            + ",\"fatePoints\":" + fatePoints + "}", FateLockedBundle.RunState.class);
    }
}
