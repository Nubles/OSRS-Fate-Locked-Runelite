package com.fatelocked.detection;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.FateEventType;

/**
 * The chat line for an event saved to the Roll inbox (plan decision 15), such as "Attack level 71:
 * added to your Roll inbox." Only a saved event gets one, so seeing something twice says so once.
 */
public final class RollReminder
{
    private RollReminder()
    {
    }

    /** The line for this event; null for one with nothing to name it by. */
    public static String text(DetectedEvent event)
    {
        String what = what(event);
        return what == null ? null : what + ": added to your Roll inbox.";
    }

    /** What happened, in the game's words where the tracker's id isn't one. */
    private static String what(DetectedEvent event)
    {
        if (event.getType() == FateEventType.PET_DROP) return "A new pet";
        String label = event.getCanonicalLabel();
        if (event.getType() == null || label == null) return null;
        switch (event.getType())
        {
            case SKILL_LEVEL:
                return label.replace(" Level ", " level ");
            case QUEST:
                Object quest = event.getEvidence() == null ? null : event.getEvidence().get("quest");
                return (quest instanceof String ? (String) quest : label) + " complete";
            case COMBAT_ACHIEVEMENT:
                return "Combat task " + label;
            case COLLECTION_LOG:
                return "Collection log item " + label;
            case CLUE_CASKET:
                return label;
            case BOSS_KILL:
                return label + " kill";
            case RAID_COMPLETION:
                return label + " completion";
            case SLAYER_TASK:
                return "Slayer task " + label;
            case DIARY_TASK:
                return DiaryTiers.FULL_NAMES.getOrDefault(label, label) + " diary";
            default:
                return null;
        }
    }
}
