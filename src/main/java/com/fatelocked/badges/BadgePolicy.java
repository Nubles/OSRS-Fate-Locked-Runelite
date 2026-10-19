package com.fatelocked.badges;

import java.util.concurrent.TimeUnit;
import lombok.Value;

/**
 * When to put the player's own badge on the relay's public list, and when to take it off
 * (CONTRACT section 2). Pure: what is known now in, one request (or none) out.
 *
 * <p>A badge goes up only with Share my badge and online sync on, a pairing in use, and the
 * logged-in character the one the rules are bound to; again when the name or tier changes, and
 * otherwise at most once a day, which keeps the relay's 30 days alive. Turning sharing off, or a
 * pairing that changed or went, takes it down once, from the code it went up under.
 */
public final class BadgePolicy
{
    /** The longest a shared badge goes without being sent again. */
    static final long REFRESH_MILLIS = TimeUnit.DAYS.toMillis(1);

    /** What the plugin knows now. */
    @Value
    public static class Facts
    {
        /** Share my badge. */
        boolean share;
        /** Online sync: the player's consent to the relay. */
        boolean sync;
        /** The pairing code in use, or "" for none. */
        String code;
        /** The logged-in character's name, when the rules in force are bound to them; else null. */
        String boundName;
        /** Their run's tier, or {@link BadgeTier#NONE}. */
        int tier;
    }

    /**
     * The badge the relay holds for this player, as last sent: the code it went up under (kept
     * before the send, so a badge whose reply was lost still comes down), and the name, tier and
     * time of the last send the relay accepted (null name and 0 time before one).
     */
    @Value
    public static class Shared
    {
        public static final Shared NONE = new Shared("", null, BadgeTier.NONE, 0);

        String code;
        String name;
        int tier;
        long at;

        boolean live()
        {
            return !code.isEmpty();
        }
    }

    /** What to send. */
    public enum Kind
    {
        NOTHING,
        POST,
        DELETE
    }

    /** One request, or none. */
    @Value
    public static class Action
    {
        static final Action NOTHING = new Action(Kind.NOTHING, null, null, BadgeTier.NONE);

        Kind kind;
        String code;
        String name;
        int tier;
    }

    private BadgePolicy()
    {
    }

    public static Action decide(Facts facts, Shared shared, long now)
    {
        if (!facts.isSync())
        {
            // Consent withdrawn: nothing goes to the relay, a removal included. The relay drops
            // a badge 30 days after its last send.
            return Action.NOTHING;
        }
        String code = facts.getCode() == null ? "" : facts.getCode();
        if (shared.live() && (!facts.isShare() || !shared.getCode().equals(code)))
        {
            return new Action(Kind.DELETE, shared.getCode(), null, BadgeTier.NONE);
        }
        if (!facts.isShare() || code.isEmpty() || !BadgeTier.valid(facts.getTier()))
        {
            return Action.NOTHING;
        }
        String name = BadgeNames.shareable(facts.getBoundName());
        if (name == null)
        {
            return Action.NOTHING;
        }
        boolean same = shared.live() && name.equals(shared.getName()) && facts.getTier() == shared.getTier();
        if (same && now - shared.getAt() < REFRESH_MILLIS && now >= shared.getAt())
        {
            return Action.NOTHING;
        }
        return new Action(Kind.POST, code, name, facts.getTier());
    }
}
