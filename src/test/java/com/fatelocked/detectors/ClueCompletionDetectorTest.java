package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ClueCompletionDetectorTest
{
    private final ClueCompletionDetector detector = new ClueCompletionDetector();

    @Test
    public void aClueRewardIsACompletionOfItsTier()
    {
        // The event RuneLite's loot tracker posts for "You have completed 12
        // hard Treasure Trails."
        DetectedEvent clue = detector.detect("EVENT", "Clue Scroll (Hard)").get();

        assertEquals(FateEventType.CLUE_CASKET, clue.getType());
        assertEquals("Clue Scroll (Hard)", clue.getCanonicalLabel());
        assertEquals(EventConfidence.EXACT, clue.getConfidence());
        assertEquals("hard", clue.getEvidence().get("tier"));
        assertEquals("beginner", detector.detect("EVENT", "Clue Scroll (Beginner)").get()
            .getEvidence().get("tier"));
    }

    @Test
    public void aCasketInOtherLootIsNotAClueCompletion()
    {
        assertFalse(detector.detect("EVENT", "Casket").isPresent());
        assertFalse(detector.detect("EVENT", "Tempoross").isPresent());
        assertFalse(detector.detect("NPC", "Clue Scroll (Hard)").isPresent());
        assertFalse(detector.detect("EVENT", null).isPresent());
    }
}
