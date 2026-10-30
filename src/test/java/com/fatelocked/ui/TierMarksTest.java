package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import org.junit.Test;

/** The game tabs' tier marks read like the tracker's: I to X in the tier's gem colour. */
public class TierMarksTest
{
    @Test
    public void tiersReadAsRomanNumeralsUpToX()
    {
        assertEquals("I", TierMarks.roman(1));
        assertEquals("IV", TierMarks.roman(4));
        assertEquals("IX", TierMarks.roman(9));
        assertEquals("X", TierMarks.roman(10));
        assertEquals("X", TierMarks.roman(12));
        assertEquals("I", TierMarks.roman(0));
    }

    @Test
    public void eachTierHasItsGemsColour()
    {
        assertEquals(new Color(0xd9cfc0), TierMarks.gem(1));
        assertEquals(new Color(0xf5a623), TierMarks.gem(10));
        assertEquals(TierMarks.gem(10), TierMarks.gem(11));
    }

    @Test
    public void aSkillTierOpensTenLevelsEachAndEveryLevelAtX()
    {
        assertEquals(10, TierMarks.levelCap(1));
        assertEquals(90, TierMarks.levelCap(9));
        assertEquals(99, TierMarks.levelCap(10));
    }
}
