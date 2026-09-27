package com.fatelocked.sidebar;

import lombok.Value;

/** The Connection and backup section: online sync, the pairing, and the backup tools. */
@Value
public class ConnectionModel
{
    boolean onlineSync;
    /** The pairing by its last four characters, or null when unpaired. */
    String pairing;
    /** Where the rules in use came from, and when, or null. */
    String source;
    /** Connect, re-pair or cancel re-pairing: what the pairing button does now. */
    CardAction pairingAction;
    boolean canCheckNow;
    boolean canDisconnect;
    /** A short state for the closed header, such as "Online" or "Off". */
    String summary;
}
