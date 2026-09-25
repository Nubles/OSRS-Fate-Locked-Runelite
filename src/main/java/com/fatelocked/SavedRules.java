package com.fatelocked;

import java.time.Instant;

/** The last rules the plugin accepted, as kept for the next start. */
final class SavedRules
{
    private final String payload;
    private final FateLockedPlugin.RulesSource source;
    private final Instant savedAt;
    private final String relayVersion;
    private final String pairingTag;

    /**
     * @param payload the bundle as the plugin received it, plain or "FLGZ:"
     * @param relayVersion the relay's version of these rules; relay rules only
     * @param pairingTag PairingSupport.tag of the pairing that sent them; relay rules only
     */
    SavedRules(
        String payload,
        FateLockedPlugin.RulesSource source,
        Instant savedAt,
        String relayVersion,
        String pairingTag)
    {
        if (payload == null || payload.trim().isEmpty() || savedAt == null
            || source == null || source == FateLockedPlugin.RulesSource.NONE)
        {
            throw new IllegalArgumentException("saved rules need a payload, a source and a time");
        }
        this.payload = payload;
        this.source = source;
        this.savedAt = savedAt;
        this.relayVersion = relayVersion;
        this.pairingTag = pairingTag;
    }

    String getPayload()
    {
        return payload;
    }

    FateLockedPlugin.RulesSource getSource()
    {
        return source;
    }

    Instant getSavedAt()
    {
        return savedAt;
    }

    String getRelayVersion()
    {
        return relayVersion;
    }

    String getPairingTag()
    {
        return pairingTag;
    }
}
