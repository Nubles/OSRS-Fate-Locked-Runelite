package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RaidDetectorTest
{
    private final RaidDetector detector = new RaidDetector();

    @Test
    public void aRaidsRewardChestIsARaidCompletion()
    {
        DetectedEvent raid = detector.detect("EVENT", "Chambers of Xeric", 0).get();

        assertEquals(FateEventType.RAID_COMPLETION, raid.getType());
        assertEquals(EventConfidence.EXACT, raid.getConfidence());
    }

    @Test
    public void aHighLevelMonsterIsNotABoss()
    {
        // A Mithril dragon on a Slayer task is level 304, and was nudged as a
        // boss kill on every kill.
        assertFalse(detector.detect("NPC", "Mithril dragon", 304).isPresent());
        assertFalse(detector.detect("NPC", "Unknown creature", 500).isPresent());
    }
}
