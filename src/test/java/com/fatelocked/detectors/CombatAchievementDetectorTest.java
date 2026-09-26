package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The game's own lines, from RuneLite's ScreenshotPluginTest, not invented
 * ones: an invented line let the plugin store the game's colour marker and
 * points as part of every task's name.
 */
public class CombatAchievementDetectorTest
{
    private final CombatAchievementDetector detector = new CombatAchievementDetector();

    @Test
    public void readsTheTaskNameWithoutTheMarkerTagOrPoints()
    {
        assertTask("Into the Den of Giants", "easy",
            "Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col>.");
        assertTask("I'd Rather Not Learn", "medium",
            "Congratulations, you've completed a medium combat task: @ach_comp@I'd Rather Not Learn</col>.");
        assertTask("Why Cook?", "hard",
            "Congratulations, you've completed a hard combat task: @ach_comp@Why Cook?</col>.");
        assertTask("From Dusk...", "elite",
            "Congratulations, you've completed an elite combat task: @ach_comp@From Dusk...</col>.");
        assertTask("Perfect Olm (Trio)", "master",
            "Congratulations, you've completed a master combat task: @ach_comp@Perfect Olm (Trio)</col>.");
        assertTask("Chambers of Xeric: CM (5-Scale) Speed-Runner", "grandmaster",
            "Congratulations, you've completed a grandmaster combat task: "
                + "@ach_comp@Chambers of Xeric: CM (5-Scale) Speed-Runner</col>.");
        assertTask("Egniol Diet II", "grandmaster",
            "Congratulations, you've completed a grandmaster combat task: @ach_comp@Egniol Diet II</col> (6 points).");
    }

    @Test
    public void readsTheLineWithItsTagsRemovedToo()
    {
        assertTask("Egniol Diet II", "grandmaster",
            "Congratulations, you've completed a grandmaster combat task: @ach_comp@Egniol Diet II (6 points).");
        assertTask("From Dusk...", "elite",
            "Congratulations, you've completed an elite combat task: @ach_comp@From Dusk....");
    }

    @Test
    public void aLineWithNoTaskIsUncertain()
    {
        DetectedEvent detected = detector.detect("Combat task complete");

        assertNull(detected.getCanonicalLabel());
        assertEquals(EventConfidence.UNCERTAIN, detected.getConfidence());
    }

    private void assertTask(String task, String tier, String message)
    {
        DetectedEvent detected = detector.detect(message);
        assertEquals(message, task, detected.getCanonicalLabel());
        assertEquals(message, EventConfidence.EXACT, detected.getConfidence());
        assertEquals(message, tier, detected.getEvidence().get("tier"));
    }
}
