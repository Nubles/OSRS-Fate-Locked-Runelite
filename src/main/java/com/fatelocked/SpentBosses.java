package com.fatelocked;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.FateEventType;
import com.fatelocked.rules.RuneliteRulesManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * The bosses and raids whose kills the tracker won't roll again this run: in Vanilla, each one that
 * has given every Standard Key it holds, as Brutus has after his one. RuneLite neither records their
 * kills nor offers the ones it already has, so a spent boss doesn't say "added to your Roll inbox"
 * after every kill. Rules without the tracker's list spend none.
 */
final class SpentBosses
{
    private SpentBosses()
    {
    }

    /** Whether the rules say a kill of this boss, or a completion of this raid, can't roll. */
    static boolean covers(FateLockedBundle bundle, FateEventType type, String label)
    {
        if (type != FateEventType.BOSS_KILL && type != FateEventType.RAID_COMPLETION || label == null)
        {
            return false;
        }
        RuneliteRulesManifest rules = bundle == null ? null : bundle.getRules();
        List<String> spent = rules == null ? null : rules.getSpentBosses();
        if (spent == null)
        {
            return false;
        }
        for (String boss : spent)
        {
            if (boss.equalsIgnoreCase(label.trim()))
            {
                return true;
            }
        }
        return false;
    }

    /** The Roll inbox's events without a spent boss's kills, in the same order. */
    static List<DetectedEventStore.Entry> without(FateLockedBundle bundle, List<DetectedEventStore.Entry> entries)
    {
        List<DetectedEventStore.Entry> kept = new ArrayList<>();
        for (DetectedEventStore.Entry entry : entries)
        {
            if (!covers(bundle, entry.getEvent().getEventType(), entry.getEvent().getCanonicalLabel()))
            {
                kept.add(entry);
            }
        }
        return kept;
    }
}
