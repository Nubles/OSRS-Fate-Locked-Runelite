package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Font;
import org.junit.Test;

public class TypeTest
{
    @Test
    public void textIsDrawnInRuneLitesOwnFontsAtTheirOwnSize()
    {
        for (Font font : new Font[]{Type.title(), Type.body(), Type.small()})
        {
            assertTrue(font.getFamily(), font.getFamily().startsWith("RuneScape"));
            assertEquals(16, font.getSize());
        }
        assertTrue(Type.title().isBold());
        assertNotEquals(Type.body().getFamily(), Type.small().getFamily());
    }
}
