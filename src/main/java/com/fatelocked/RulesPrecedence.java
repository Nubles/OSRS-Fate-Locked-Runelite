package com.fatelocked;

/**
 * Which rules may replace the ones in force. The tracker wins while
 * paired. A player's own import always applies, and the tracker's copy
 * wins again on its next check. Rules found at startup, saved by the last
 * start or read from the newest backup file, only fill an empty slot, so
 * they can never undo anything that arrived since.
 */
final class RulesPrecedence
{
    /** How a rule set arrived. */
    enum Arrival
    {
        /** Saved by the last start (saved-rules.json). */
        SAVED,
        /** The newest backup file, read at startup when nothing was saved. */
        STARTUP_FILE,
        /** Sent by the tracker relay. */
        RELAY,
        /** Imported by the player: the clipboard, its hotkey or "Load newest backup file". */
        IMPORT
    }

    private RulesPrecedence()
    {
    }

    static boolean mayReplace(FateLockedPlugin.RulesSource active, Arrival arrival)
    {
        switch (arrival)
        {
            case SAVED:
            case STARTUP_FILE:
                return active == FateLockedPlugin.RulesSource.NONE;
            case RELAY:
            case IMPORT:
                return true;
            default:
                throw new IllegalArgumentException("Unknown arrival " + arrival);
        }
    }
}
