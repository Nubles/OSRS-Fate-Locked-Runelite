package com.fatelocked.rules;

import lombok.Value;

/**
 * One answer from the tracker's rules: the status, what the thing is called,
 * and why it is not allowed. {@link DecisionService} is the only source.
 */
@Value
public class Decision
{
    public enum Source
    {
        /** A chunk's entry in the v4 rules. */
        CHUNK,
        /** A row (bank, shop, NPC, …) inside a chunk's v4 rules. */
        ROW,
        /** An item's equipment tier. */
        ITEM,
        /** A mobility unlock such as Fairy Rings. */
        MOBILITY,
        /** The v1–3 root-field rules of an older export. */
        LEGACY,
        /** The rules don't apply to this character, or none are loaded. */
        TRUST,
        /** The rules say nothing about this place. */
        UNMAPPED
    }

    PermissionStatus status;
    /** What was asked about, as the rules name it; null when they don't. */
    String label;
    /** Why it isn't allowed, in the tracker's words; null when allowed or not given. */
    String reason;
    Source source;

    public boolean isLocked()
    {
        return status == PermissionStatus.LOCKED;
    }

    /** False when the rules didn't decide: a trust gate or an unmapped place. */
    public boolean isAuthored()
    {
        return source != Source.TRUST && source != Source.UNMAPPED;
    }
}
