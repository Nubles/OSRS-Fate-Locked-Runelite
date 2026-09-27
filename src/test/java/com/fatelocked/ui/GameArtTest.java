package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GameArtTest
{
    private final SpriteManager sprites = mock(SpriteManager.class);
    private final ItemManager items = mock(ItemManager.class);
    private final GameArt art = new GameArt(sprites, items);

    @Test
    @SuppressWarnings("unchecked")
    public void aSpriteArrivesOnTheSwingThreadOnceAndIsKept() throws Exception
    {
        List<BufferedImage> first = new ArrayList<>();
        List<BufferedImage> second = new ArrayList<>();
        onEdt(() -> {
            art.load(Art.STRICT_MODE, first::add);
            art.load(Art.STRICT_MODE, second::add);
        });
        ArgumentCaptor<Consumer<BufferedImage>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(sprites, times(1)).getSpriteAsync(eq(SpriteID.Magicon2.TELE_BLOCK), eq(0), callback.capture());

        BufferedImage teleBlock = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        boolean[] onEdt = new boolean[1];
        callback.getValue().accept(teleBlock);
        onEdt(() -> onEdt[0] = true);
        assertEquals(List.of(teleBlock), first);
        assertEquals("both callers waiting are answered", List.of(teleBlock), second);

        List<BufferedImage> later = new ArrayList<>();
        onEdt(() -> art.load(Art.STRICT_MODE, image -> {
            later.add(image);
            onEdt[0] = SwingUtilities.isEventDispatchThread();
        }));
        assertSame(teleBlock, later.get(0));
        assertEquals(true, onEdt[0]);
        verify(sprites, times(1)).getSpriteAsync(anyInt(), anyInt(), any(Consumer.class));
    }

    @Test
    public void anItemIsDrawnAsItsStackAndArrivesWhenTheGameHasRenderedIt() throws Exception
    {
        AsyncBufferedImage stardust = new AsyncBufferedImage(mock(ClientThread.class), 36, 32,
            BufferedImage.TYPE_INT_ARGB);
        when(items.getImage(ItemID.STAR_DUST, 175, false)).thenReturn(stardust);
        List<BufferedImage> got = new ArrayList<>();
        onEdt(() -> art.load(Art.FATE_POINTS, got::add));
        onEdt(() -> { });
        assertEquals("nothing until the game renders it", List.of(), got);

        stardust.loaded();
        onEdt(() -> { });
        assertEquals(List.of(stardust), got);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void artThatNeverLoadsIsAskedForAgainNextTime() throws Exception
    {
        onEdt(() -> art.load(Art.PLACE, image -> { }));
        ArgumentCaptor<Consumer<BufferedImage>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(sprites).getSpriteAsync(eq(SpriteID.WorldmapIcon.PLANET), eq(0), callback.capture());
        List<BufferedImage> got = new ArrayList<>();
        onEdt(() -> art.load(Art.PLACE, got::add));
        callback.getValue().accept(null);
        onEdt(() -> { });
        assertEquals(List.of(), got);

        onEdt(() -> art.load(Art.PLACE, got::add));
        verify(sprites, times(2)).getSpriteAsync(eq(SpriteID.WorldmapIcon.PLANET), eq(0), any(Consumer.class));
        verify(items, never()).getImage(anyInt(), anyInt(), any(Boolean.class));
    }

    private static void onEdt(Runnable task) throws Exception
    {
        SwingUtilities.invokeAndWait(task);
    }
}
