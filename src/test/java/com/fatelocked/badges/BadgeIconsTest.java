package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.game.ChatIconManager;
import org.junit.Test;

/** The keys draw in chat as they were drawn: game chat icon size, crisp, one look a tier. */
public class BadgeIconsTest
{
    private final BadgeIcons icons = new BadgeIcons();

    @Test
    public void eachKeyIsAChatIconsSize()
    {
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            BufferedImage key = icons.image(tier);
            assertNotNull(BadgeIcons.FILES[tier], key);
            assertEquals(BadgeIcons.FILES[tier], 13, key.getHeight());
            assertTrue(BadgeIcons.FILES[tier], key.getWidth() >= 7 && key.getWidth() <= 13);
        }
        assertNull(icons.image(BadgeTier.NONE));
        assertNull(icons.image(BadgeTier.COUNT));
    }

    /**
     * RuneLite turns a chat icon into an indexed sprite: a pixel less than opaque isn't drawn,
     * and pure black is its transparent colour, so the outline is (0, 0, 1).
     */
    @Test
    public void everyPixelIsOpaqueOrClearAndNoneIsPureBlack()
    {
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            BufferedImage key = icons.image(tier);
            int opaque = 0;
            for (int y = 0; y < key.getHeight(); y++)
            {
                for (int x = 0; x < key.getWidth(); x++)
                {
                    int argb = key.getRGB(x, y);
                    int alpha = argb >>> 24;
                    assertTrue(BadgeIcons.FILES[tier] + " at " + x + "," + y, alpha == 0 || alpha == 255);
                    if (alpha == 255)
                    {
                        opaque++;
                        assertNotEquals(BadgeIcons.FILES[tier] + " at " + x + "," + y, 0, argb & 0xFFFFFF);
                    }
                }
            }
            assertTrue(opaque > 50);
        }
    }

    @Test
    public void eachTierLooksDifferent()
    {
        Set<Integer> looks = new HashSet<>();
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            BufferedImage key = icons.image(tier);
            // The middle of the key's shaft.
            looks.add(key.getRGB(4, 9));
        }
        assertEquals(BadgeTier.COUNT, looks.size());
    }

    @Test
    public void theKeysAreRegisteredOnce()
    {
        ChatIconManager chatIcons = mock(ChatIconManager.class);
        int[] next = {10};
        when(chatIcons.registerChatIcon(any(BufferedImage.class))).thenAnswer(invocation -> next[0]++);
        when(chatIcons.chatIconIndex(12)).thenReturn(77);
        assertEquals(-1, icons.chatIndex(BadgeTier.PATHFINDER));

        icons.register(chatIcons);
        icons.register(chatIcons);
        verify(chatIcons, times(BadgeTier.COUNT)).registerChatIcon(any(BufferedImage.class));
        assertEquals(77, icons.chatIndex(BadgeTier.PATHFINDER));
        assertEquals(-1, icons.chatIndex(BadgeTier.NONE));
    }
}
