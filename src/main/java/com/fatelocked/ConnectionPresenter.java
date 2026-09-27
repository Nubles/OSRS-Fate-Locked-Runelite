package com.fatelocked;

import com.fatelocked.panel.LocalTimeText;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.ConnectionModel;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The Connection and backup section: the online-sync switch, the pairing by its last
 * four characters, where the rules in force came from, and which pairing button to
 * offer, as SyncView decides it. Pure.
 */
final class ConnectionPresenter
{
    private ConnectionPresenter()
    {
    }

    /**
     * @param onlineSync whether online sync is on
     * @param paired     whether a pairing is saved
     * @param source     where the rules in force came from
     * @param exportedAt when the tracker exported them, or null
     */
    static ConnectionModel present(TrackerConnectionSnapshot snapshot, boolean onlineSync, boolean paired,
        FateLockedPlugin.RulesSource source, Instant exportedAt, Instant now, ZoneId zone)
    {
        SyncView view = SyncView.of(snapshot, now, zone);
        String pairing = snapshot.getPairingEnding() == null ? null : "…" + snapshot.getPairingEnding();
        return new ConnectionModel(onlineSync, pairing, source(source, snapshot, exportedAt, now, zone),
            action(view.connect), view.canCheckNow, paired,
            !onlineSync ? "Off" : snapshot.getState() == TrackerConnectionState.CONNECTED ? "Online"
                : snapshot.getMessage());
    }

    private static String source(FateLockedPlugin.RulesSource source, TrackerConnectionSnapshot snapshot,
        Instant exportedAt, Instant now, ZoneId zone)
    {
        switch (source)
        {
            case RELAY:
                return snapshot.getLastSync() == null ? "Rules from the tracker, saved by the last start."
                    : "Rules from the tracker, last synced at " + LocalTimeText.of(snapshot.getLastSync(), now, zone)
                        + ".";
            case IMPORT:
                return "Rules from the clipboard" + exported(exportedAt, now, zone);
            case FILE:
                return "Rules from a backup file" + exported(exportedAt, now, zone);
            default:
                return null;
        }
    }

    private static String exported(Instant exportedAt, Instant now, ZoneId zone)
    {
        return exportedAt == null ? "." : ", exported at " + LocalTimeText.of(exportedAt, now, zone) + ".";
    }

    private static CardAction action(SyncView.Connect connect)
    {
        switch (connect)
        {
            case TURN_ON_SYNC:
                return CardAction.TURN_ON_SYNC;
            case REPAIR:
                return CardAction.REPAIR;
            case CANCEL_REPAIR:
                return CardAction.CANCEL_REPAIR;
            default:
                return CardAction.CONNECT;
        }
    }
}
