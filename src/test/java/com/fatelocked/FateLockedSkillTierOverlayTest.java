package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import org.junit.Test;

/** The Skills tab's tier marks find each skill by its guide option and the run's tier by name. */
public class FateLockedSkillTierOverlayTest
{
    @Test
    public void aSkillIsFoundByItsGuideOption()
    {
        assertEquals("Attack", FateLockedSkillTierOverlay.skillOf(new String[]{null, "View <col=ff9040>Attack</col> guide"}));
        assertEquals("Runecraft", FateLockedSkillTierOverlay.skillOf(new String[]{"View Runecraft guide"}));
        assertNull(FateLockedSkillTierOverlay.skillOf(new String[]{"Toggle"}));
        assertNull(FateLockedSkillTierOverlay.skillOf(null));
    }

    @Test
    public void theTierMatchesTheTrackersNameWhateverItsCase()
    {
        Map<String, Integer> tiers = Map.of("Attack", 3, "Sailing", 0);
        assertEquals(Integer.valueOf(3), FateLockedSkillTierOverlay.tierOf(tiers, "attack"));
        assertEquals(Integer.valueOf(0), FateLockedSkillTierOverlay.tierOf(tiers, "Sailing"));
        assertNull(FateLockedSkillTierOverlay.tierOf(tiers, "Hunter"));
    }
}
