package com.fatelocked;

import com.fatelocked.rules.PermissionStatus;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;

/**
 * What stepping into a chunk says (U10, decision 6), once per area rather than once per chunk.
 *
 * <ul>
 * <li>A routine line is posted when the area changes, if area changes are announced.</li>
 * <li>The locked alert fires on entering a locked area, from unlocked land or from a different
 *     locked area. Its line follows the alert setting. The sound and the fade are for coming in
 *     from unlocked land; a new locked area next to the last one posts its line only.</li>
 * <li>The same locked area waits a minute before it alerts again, so walking along a border
 *     doesn't repeat it.</li>
 * <li>A place the rules don't decide says nothing. Not ready is owned, so it never alerts.</li>
 * </ul>
 *
 * <p>Everything is forgotten when a session starts or the plugin stops, so the next entry
 * alerts afresh. Client thread only.
 */
final class LockedAreaAlerts
{
    /** The same locked area alerts again after this many game ticks: a minute. */
    static final int QUIET_TICKS = 100;

    /** What one step says: a chat line, and for the locked alert its sound and fade. */
    @Value
    static class Alert
    {
        boolean line;
        boolean sound;
        boolean fade;
    }

    static final Alert NONE = new Alert(false, false, false);
    private static final Alert LINE = new Alert(true, false, false);

    private String lastArea;
    private PermissionStatus lastStatus;
    private final Map<String, Integer> alertedAt = new HashMap<>();

    /**
     * Step into a chunk of this area, on this game tick.
     *
     * @param area what groups chunks: the area's name, else the tracker's reason (the sea is
     *     one area under its reason), else the chunk itself
     */
    Alert enter(PermissionStatus status, String area, int tick, FateLockedConfig.LockedAreaAlert level,
        boolean announce)
    {
        boolean sameArea = area.equals(lastArea);
        boolean fromLocked = lastStatus == PermissionStatus.LOCKED;
        lastArea = area;
        lastStatus = status;
        if (status == PermissionStatus.UNKNOWN || sameArea)
        {
            return NONE;
        }
        if (status != PermissionStatus.LOCKED)
        {
            return announce ? LINE : NONE;
        }
        Integer last = alertedAt.get(area);
        if (last != null && tick - last < QUIET_TICKS)
        {
            return NONE;
        }
        alertedAt.put(area, tick);
        return new Alert(level.chat(), !fromLocked && level.sound(), !fromLocked && level.fade());
    }

    /** A new session, or the plugin stopping: the next entry alerts afresh. */
    void forget()
    {
        lastArea = null;
        lastStatus = null;
        alertedAt.clear();
    }
}
