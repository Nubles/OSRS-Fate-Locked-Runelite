package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class NavigationIconTest
{
    /** RuneLite shows the sidebar button's icon at 16 px, rescaling anything else smoothly into a blur. */
    @Test
    public void theSidebarIconIsTheCrystalKeyAtTheSizeRuneLiteShowsIt()
    {
        BufferedImage icon = FateLockedPlugin.navigationIcon();
        assertEquals(16, icon.getWidth());
        assertEquals(16, icon.getHeight());
        int opaque = 0;
        int clear = 0;
        for (int y = 0; y < 16; y++)
        {
            for (int x = 0; x < 16; x++)
            {
                int alpha = icon.getRGB(x, y) >>> 24;
                if (alpha == 255) opaque++;
                if (alpha == 0) clear++;
            }
        }
        assertTrue("a key, not a square: " + opaque, opaque > 40 && clear > 60);
    }
}
