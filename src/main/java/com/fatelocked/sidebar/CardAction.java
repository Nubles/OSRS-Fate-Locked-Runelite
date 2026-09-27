package com.fatelocked.sidebar;

/** Something the player can do from a sidebar card, by the words on its button. */
public enum CardAction
{
    CONNECT("Connect tracker"),
    TURN_ON_SYNC("Turn on online sync"),
    OPEN_PAGE_AGAIN("Open page again"),
    CANCEL_PAIRING("Cancel"),
    CHECK_NOW("Check now"),
    OPEN_TRACKER("Open web tracker"),
    USE_BACKUP("Use a backup instead"),
    IMPORT_CLIPBOARD("Import from clipboard"),
    LOAD_BACKUP_FILE("Load newest backup file"),
    REPAIR("Re-pair tracker…"),
    CANCEL_REPAIR("Cancel re-pairing"),
    DISCONNECT("Disconnect"),
    PAUSE_STRICT_MODE("Pause 60s"),
    RESUME_STRICT_MODE("Resume");

    private final String label;

    CardAction(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }
}
