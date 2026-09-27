package com.fatelocked;

import com.fatelocked.panel.LocalTimeText;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The sidebar's short messages (C8, R12): what happened, in a sentence. They used to
 * count "regions", which were the 13 continents, and read as lower-case fragments. A
 * notice clears itself after 8 seconds; the status card says what lasts.
 */
final class Notices
{
    static final String NO_BACKUP_FILE = "No backup file in .runelite/fate-locked, so your rules are unchanged.";
    static final String BACKUP_UNREADABLE = "Couldn't read the backup file, so your rules are unchanged.";
    static final String CLIPBOARD_UNREADABLE = "Couldn't read the clipboard.";
    static final String CLIPBOARD_EMPTY = "The clipboard is empty.";
    static final String PAIRING_CODE = "That's a pairing code. Press Connect tracker to pair.";
    static final String IMPORT_FAILED = "Couldn't import that, so your rules are unchanged.";
    static final String SYNCED = "New rules arrived from the tracker.";
    static final String SYNC_NOT_ON = "Couldn't turn online sync on.";
    static final String SYNC_NOT_OFF = "Couldn't turn online sync off.";
    static final String STRICT_NOT_SAVED = "Couldn't save Strict Mode.";

    private Notices()
    {
    }

    /** Rules the last start saved, brought back. */
    static String restored(Instant savedAt, Instant now, ZoneId zone)
    {
        return "Restored the rules saved at " + LocalTimeText.of(savedAt, now, zone) + ".";
    }

    static String imported(Instant exportedAt, Instant now, ZoneId zone)
    {
        return "Imported rules from the clipboard" + exported(exportedAt, now, zone) + ".";
    }

    static String loadedBackupFile(Instant exportedAt, Instant now, ZoneId zone)
    {
        return "Loaded the newest backup file" + exported(exportedAt, now, zone) + ".";
    }

    private static String exported(Instant exportedAt, Instant now, ZoneId zone)
    {
        return exportedAt == null ? "" : ", exported at " + LocalTimeText.of(exportedAt, now, zone);
    }
}
