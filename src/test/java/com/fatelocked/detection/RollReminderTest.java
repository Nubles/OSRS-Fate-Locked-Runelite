package com.fatelocked.detection;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RollReminderTest
{
    @Test
    public void namesWhatHappenedAndWhereItWent()
    {
        assertEquals("Attack level 71: added to your Roll inbox.", text(FateEventType.SKILL_LEVEL, "Attack Level 71"));
        assertEquals("Combat task Egniol Diet II: added to your Roll inbox.",
            text(FateEventType.COMBAT_ACHIEVEMENT, "Egniol Diet II"));
        assertEquals("Collection log item Chompy bird hat: added to your Roll inbox.",
            text(FateEventType.COLLECTION_LOG, "Chompy bird hat"));
        assertEquals("Clue scroll (hard): added to your Roll inbox.", text(FateEventType.CLUE_CASKET, "Clue scroll (hard)"));
        assertEquals("Corporeal Beast kill: added to your Roll inbox.", text(FateEventType.BOSS_KILL, "Corporeal Beast"));
        assertEquals("Chambers of Xeric completion: added to your Roll inbox.",
            text(FateEventType.RAID_COMPLETION, "Chambers of Xeric"));
        assertEquals("Slayer task Abyssal demons: added to your Roll inbox.",
            text(FateEventType.SLAYER_TASK, "Abyssal demons"));
    }

    @Test
    public void aQuestOrDiaryTierIsNamedAsTheGameNamesIt()
    {
        assertEquals("Recipe for Disaster - Another Cook's Quest complete: added to your Roll inbox.",
            RollReminder.text(event(FateEventType.QUEST, "RFD: The Cook",
                Map.of("quest", "Recipe for Disaster - Another Cook's Quest"))));
        assertEquals("Cook's Assistant complete: added to your Roll inbox.", text(FateEventType.QUEST, "Cook's Assistant"));
        assertEquals("Lumbridge & Draynor Easy diary: added to your Roll inbox.",
            text(FateEventType.DIARY_TASK, "Lumbridge Easy"));
        assertEquals("Somewhere Easy diary: added to your Roll inbox.", text(FateEventType.DIARY_TASK, "Somewhere Easy"));
    }

    @Test
    public void aNewPetHasALineThoughTheGameDoesntSayWhichPet()
    {
        assertEquals("A new pet: added to your Roll inbox.", text(FateEventType.PET_DROP, null));
        assertEquals("A new pet: added to your Roll inbox.", text(FateEventType.PET_DROP, "Pet"));
    }

    @Test
    public void whatTheRollInboxDoesntOfferGetsNoLine()
    {
        assertNull(text(FateEventType.MINIGAME_COMPLETION, "Tempoross"));
        assertNull(text(FateEventType.QUEST, null));
        assertNull(text(null, "Anything"));
    }

    private static String text(FateEventType type, String label)
    {
        return RollReminder.text(event(type, label, null));
    }

    private static DetectedEvent event(FateEventType type, String label, Map<String, Object> evidence)
    {
        return DetectedEvent.builder().type(type).canonicalLabel(label).confidence(EventConfidence.EXACT)
            .detectorId("test").detectorVersion(1).evidence(evidence).build();
    }
}
