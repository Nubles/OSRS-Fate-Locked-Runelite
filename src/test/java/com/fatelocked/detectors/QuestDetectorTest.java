package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QuestDetectorTest
{
    @Test
    public void exactNameIsExactAndMissingNameIsUncertain()
    {
        QuestDetector detector = new QuestDetector();
        assertEquals(EventConfidence.EXACT,
            detector.detect("Dragon Slayer").getConfidence());
        assertEquals(EventConfidence.UNCERTAIN,
            detector.detect(null).getConfidence());
    }

    /** The scroll titles in RuneLite's ScreenshotPluginTest, named as the tracker names the quests. */
    @Test
    public void readsTheGamesScrollTitles()
    {
        assertQuest("The Corsair Curse", "You have completed The Corsair Curse!");
        assertQuest("One Small Favour", "'One Small Favour' completed!");
        assertQuest("Hazeel Cult", "You have... kind of... completed the Hazeel Cult Quest!");
        assertQuest("Rag and Bone Man II", "You have completely completed Rag and Bone Man!");
        assertQuest("RFD: Finale", "Congratulations! You have defeated the Culinaromancer!");
        assertQuest("RFD: The Cook", "You have completed Another Cook's Quest!");
        assertQuest("Doric's Quest", "You have completed Doric's Quest!");
        assertQuest("Sins of the Father", "Sins of the Father forgiven!");
    }

    @Test
    public void keepsQuestOnlyWhereItIsPartOfTheName()
    {
        assertQuest("Heroes' Quest", "You have completed Heroes' Quest!");
        assertQuest("Waterfall Quest", "You have completed the Waterfall Quest!");
        assertQuest("Dragon Slayer", "You have completed the Dragon Slayer Quest!");
    }

    @Test
    public void aLineThatNamesNoQuestHasNoName()
    {
        assertNull(QuestDetector.questName("Quest points: 50"));
        assertNull(QuestDetector.questName(""));
        assertNull(QuestDetector.questName(null));
    }

    private static void assertQuest(String quest, String title)
    {
        assertEquals(title, quest, QuestDetector.questName(title));
    }
}
