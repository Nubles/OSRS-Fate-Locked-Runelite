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
     * @param arrival    how they arrived (restored from disk at start, or sent this session), or null
     * @param exportedAt when the tracker exported them, or null
     */
    static ConnectionModel present(TrackerConnectionSnapshot snapshot, boolean onlineSync, boolean paired,
        FateLockedPlugin.RulesSource source, RulesPrecedence.Arrival arrival, Instant exportedAt, Instant now,
        ZoneId zone)
    {
        SyncView view = SyncView.of(snapshot, now, zone);
        String pairing = snapshot.getPairingEnding() == null ? null : "…" + snapshot.getPairingEnding();
        return new ConnectionModel(onlineSync, pairing, source(source, arrival, snapshot, exportedAt, now, zone),
            action(view.connect), view.canCheckNow, paired,
            !onlineSync ? "Off" : snapshot.getState() == TrackerConnectionState.CONNECTED ? "Online"
                : snapshot.getMessage());
    }

    private static String source(FateLockedPlugin.RulesSource source, RulesPrecedence.Arrival arrival,
        TrackerConnectionSnapshot snapshot, Instant exportedAt, Instant now, ZoneId zone)
    {
        switch (source)
        {
            case RELAY:
                // With no sync time: restored from disk at start, or sent this session before the
                // connection forgot its sync time (sync off, Disconnect, a new pairing).
                String noSync = arrival == RulesPrecedence.Arrival.SAVED ? ", saved by the last start." : ".";
                if (exportedAt == null)
                {
                    return snapshot.getLastSync() == null ? "Rules from the tracker" + noSync
                        : "Rules from the tracker, last synced at "
                            + LocalTimeText.of(snapshot.getLastSync(), now, zone) + ".";
                }
                // When the tracker sent them, apart from when RuneLite last heard from the relay.
                String sent = "Rules the tracker sent at " + LocalTimeText.of(exportedAt, now, zone);
                return snapshot.getLastSync() == null ? sent + noSync
                    : sent + ", last checked at " + LocalTimeText.of(snapshot.getLastSync(), now, zone) + ".";
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
