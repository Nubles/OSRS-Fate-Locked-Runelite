package com.fatelocked.sidebar;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Roll inbox card from this run's events (Stage 4, C1): the newest listed, the rest counted, and
 * whose they are while the run is linked to no one (plan decision 9).
 */
public final class RollInboxPresenter
{
    /** A row whose event has no name, which no detector in this build makes but the pet one. */
    static final String UNNAMED = "Needs identification";
    /** A pet's row: the game doesn't say which pet, so the tracker asks. */
    static final String NEW_PET = "New pet";

    private RollInboxPresenter()
    {
    }

    /**
     * @param offered this run's events the Roll inbox offers, newest first
     * @param linked  whether the run is linked to a character
     * @param notice  what the last copy did, or null
     * @param quiet   why RuneLite notices nothing for the character logged in, or null
     */
    public static RollInboxModel present(List<DetectedEventStore.Entry> offered, int warnings, boolean saveFailed,
        boolean linked, String notice, String quiet)
    {
        List<RollInboxModel.Row> rows = new ArrayList<>();
        Set<String> characters = new LinkedHashSet<>();
        int copied = 0;
        for (DetectedEventStore.Entry entry : offered)
        {
            FateEvent event = entry.getEvent();
            boolean isCopied = entry.getStatus() == DetectedEventStore.Status.COPIED;
            if (isCopied) copied++;
            if (event.getAccount() != null && !event.getAccount().trim().isEmpty())
            {
                characters.add(event.getAccount().trim());
            }
            if (rows.size() < RollInboxModel.MAX_ROWS)
            {
                String label = event.getCanonicalLabel();
                rows.add(new RollInboxModel.Row(event.getEventId(), event.getEventType(),
                    label != null && !label.trim().isEmpty() ? label
                        : event.getEventType() == FateEventType.PET_DROP ? NEW_PET : UNNAMED, skill(event),
                    event.getConfidence() == EventConfidence.UNCERTAIN, isCopied));
            }
        }
        return RollInboxModel.builder()
            .rows(rows)
            .more(offered.size() - rows.size())
            .newEvents(offered.size() - copied)
            .copied(copied)
            .warnings(warnings)
            .saveFailed(saveFailed)
            .character(linked || characters.isEmpty() ? null : listed(new ArrayList<>(characters)))
            .notice(notice)
            .quiet(quiet)
            .build();
    }

    /** The skill whose icon an event's row shows: a level's own, Slayer for a task. */
    static String skill(FateEvent event)
    {
        if (event.getEventType() == FateEventType.SLAYER_TASK) return "Slayer";
        if (event.getEventType() != FateEventType.SKILL_LEVEL || event.getEvidence() == null) return null;
        Object skill = event.getEvidence().get("skill");
        return skill instanceof String ? (String) skill : null;
    }

    /** "Zezima", "Zezima and Nubles", or "Zezima, Nubles and Lynx Titan". */
    static String listed(List<String> names)
    {
        if (names.size() < 2) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
