package com.fatelocked;

import static org.junit.Assert.assertEquals;

import com.fatelocked.LockedAreaAlerts.Alert;
import com.fatelocked.rules.PermissionStatus;
import org.junit.Test;

/** U10, decision 6: a line per area, and the locked alert once per locked area. */
public class LockedAreaAlertsTest
{
    private static final FateLockedConfig.LockedAreaAlert ALL = FateLockedConfig.LockedAreaAlert.CHAT_SOUND_FADE;
    private static final Alert FULL = new Alert(true, true, true);
    private static final Alert LINE = new Alert(true, false, false);
    private static final Alert NONE = LockedAreaAlerts.NONE;

    private final LockedAreaAlerts alerts = new LockedAreaAlerts();

    @Test
    public void walkingWithinOneLockedAreaAlertsOnce()
    {
        assertEquals(LINE, alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, ALL, true));
        assertEquals(FULL, alerts.enter(PermissionStatus.LOCKED, "Falador", 1, ALL, true));
        assertEquals(NONE, alerts.enter(PermissionStatus.LOCKED, "Falador", 2, ALL, true));
        assertEquals(NONE, alerts.enter(PermissionStatus.LOCKED, "Falador", 3, ALL, true));
    }

    /** Decision 6: the sound and fade are for coming in from unlocked land. */
    @Test
    public void aNewLockedAreaNextToTheLastPostsItsLineOnly()
    {
        alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, ALL, true);
        alerts.enter(PermissionStatus.LOCKED, "Falador", 1, ALL, true);
        assertEquals(LINE, alerts.enter(PermissionStatus.LOCKED, "Taverley", 2, ALL, true));
    }

    /** Walking along a border doesn't repeat the alert. */
    @Test
    public void theSameAreaWaitsAMinuteBeforeAlertingAgain()
    {
        alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, ALL, false);
        assertEquals(FULL, alerts.enter(PermissionStatus.LOCKED, "Falador", 10, ALL, false));
        alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 20, ALL, false);
        assertEquals("back within a minute", NONE, alerts.enter(PermissionStatus.LOCKED, "Falador", 109, ALL, false));
        alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 109, ALL, false);
        assertEquals("a minute on", FULL, alerts.enter(PermissionStatus.LOCKED, "Falador", 110, ALL, false));
    }

    @Test
    public void notReadyAndUndecidedPlacesNeverAlert()
    {
        assertEquals(NONE, alerts.enter(PermissionStatus.UNKNOWN, "chunk:1,1", 0, ALL, true));
        assertEquals("not ready is owned: its line only", LINE,
            alerts.enter(PermissionStatus.NOT_READY, "Catherby", 1, ALL, true));
        assertEquals(NONE, alerts.enter(PermissionStatus.NOT_READY, "Seers' Village", 2, ALL, false));
        assertEquals("the next area has its line", LINE, alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 3, ALL, true));
    }

    @Test
    public void routineLinesAreForAreaChangesWhenTheyAreAnnounced()
    {
        assertEquals(NONE, alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, ALL, false));
        assertEquals(LINE, alerts.enter(PermissionStatus.ALLOWED, "Draynor Village", 1, ALL, true));
        assertEquals(NONE, alerts.enter(PermissionStatus.ALLOWED, "Draynor Village", 2, ALL, true));
    }

    @Test
    public void theAlertSettingDecidesWhatTheAlertDoes()
    {
        for (FateLockedConfig.LockedAreaAlert level : FateLockedConfig.LockedAreaAlert.values())
        {
            LockedAreaAlerts fresh = new LockedAreaAlerts();
            fresh.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, level, true);
            assertEquals(level.name(), new Alert(level.chat(), level.sound(), level.fade()),
                fresh.enter(PermissionStatus.LOCKED, "Falador", 1, level, true));
        }
    }

    /** At login the next entry alerts afresh, even standing in the same locked area. */
    @Test
    public void aNewSessionAlertsAfresh()
    {
        alerts.enter(PermissionStatus.ALLOWED, "Lumbridge", 0, ALL, false);
        alerts.enter(PermissionStatus.LOCKED, "Falador", 1, ALL, false);

        alerts.forget();

        assertEquals(FULL, alerts.enter(PermissionStatus.LOCKED, "Falador", 2, ALL, false));
    }
}
