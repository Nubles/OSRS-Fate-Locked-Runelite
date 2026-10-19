package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** When the player's own badge goes up, comes down, or neither (CONTRACT section 2). */
public class BadgePolicyTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_CODE = "fedcba9876543210fedcba9876543210";
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private static BadgePolicy.Facts facts(boolean share, boolean sync, String code, String name, int tier)
    {
        return new BadgePolicy.Facts(share, sync, code, name, tier);
    }

    private static BadgePolicy.Facts sharing(String name, int tier)
    {
        return facts(true, true, CODE, name, tier);
    }

    private static BadgePolicy.Shared sent(String code, String name, int tier, long at)
    {
        return new BadgePolicy.Shared(code, name, tier, at);
    }

    private static void assertAction(BadgePolicy.Action action, BadgePolicy.Kind kind, String code, String name,
        int tier)
    {
        assertEquals(kind, action.getKind());
        assertEquals(code, action.getCode());
        assertEquals(name, action.getName());
        assertEquals(tier, action.getTier());
    }

    @Test
    public void sharingPostsTheBoundCharactersNameAndTier()
    {
        assertAction(BadgePolicy.decide(sharing("Iron Example", 2), BadgePolicy.Shared.NONE, NOW),
            BadgePolicy.Kind.POST, CODE, "Iron Example", 2);
    }

    @Test
    public void nothingIsSentWithoutEveryCondition()
    {
        BadgePolicy.Shared none = BadgePolicy.Shared.NONE;
        // Share my badge off (the default).
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(false, true, CODE, "Iron Example", 1), none, NOW).getKind());
        // Online sync off: no consent to the relay.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, false, CODE, "Iron Example", 1), none, NOW).getKind());
        // No pairing.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, true, "", "Iron Example", 1), none, NOW).getKind());
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, true, null, "Iron Example", 1), none, NOW).getKind());
        // No rules, rules bound to no one or to another character: no bound name.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, true, CODE, null, 1), none, NOW).getKind());
        // Rules that count nothing.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, true, CODE, "Iron Example", BadgeTier.NONE), none, NOW).getKind());
        // A name the relay would refuse.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, true, CODE, "Iron.Example!", 1), none, NOW).getKind());
    }

    @Test
    public void anUnchangedBadgeIsSentAtMostOnceADay()
    {
        BadgePolicy.Shared shared = sent(CODE, "Iron Example", 1, NOW - DAY + 1);
        assertEquals(BadgePolicy.Kind.NOTHING, BadgePolicy.decide(sharing("Iron Example", 1), shared, NOW).getKind());
        // The game's non-breaking space is the same name.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(sharing("Iron Example", 1), shared, NOW).getKind());
        assertAction(BadgePolicy.decide(sharing("Iron Example", 1), shared, NOW + 1),
            BadgePolicy.Kind.POST, CODE, "Iron Example", 1);
        // A clock that went back sends again rather than waiting days.
        assertEquals(BadgePolicy.Kind.POST,
            BadgePolicy.decide(sharing("Iron Example", 1), sent(CODE, "Iron Example", 1, NOW + DAY), NOW).getKind());
    }

    @Test
    public void aNewTierOrNameIsSentAtOnce()
    {
        BadgePolicy.Shared shared = sent(CODE, "Iron Example", 1, NOW - 1000);
        assertAction(BadgePolicy.decide(sharing("Iron Example", 2), shared, NOW),
            BadgePolicy.Kind.POST, CODE, "Iron Example", 2);
        assertAction(BadgePolicy.decide(sharing("New Name", 1), shared, NOW),
            BadgePolicy.Kind.POST, CODE, "New Name", 1);
        // A send whose reply never came is sent again.
        assertAction(BadgePolicy.decide(sharing("Iron Example", 1), sent(CODE, null, BadgeTier.NONE, 0), NOW),
            BadgePolicy.Kind.POST, CODE, "Iron Example", 1);
    }

    @Test
    public void turningSharingOffDeletesOnce()
    {
        BadgePolicy.Shared shared = sent(CODE, "Iron Example", 3, NOW - 1000);
        assertAction(BadgePolicy.decide(facts(false, true, CODE, "Iron Example", 3), shared, NOW),
            BadgePolicy.Kind.DELETE, CODE, null, BadgeTier.NONE);
        // Logged out, or on another character, it still comes down.
        assertAction(BadgePolicy.decide(facts(false, true, CODE, null, BadgeTier.NONE), shared, NOW),
            BadgePolicy.Kind.DELETE, CODE, null, BadgeTier.NONE);
        // Once it's down, nothing more.
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(false, true, CODE, "Iron Example", 3), BadgePolicy.Shared.NONE, NOW).getKind());
        // Even a send whose reply was lost comes down.
        assertEquals(BadgePolicy.Kind.DELETE,
            BadgePolicy.decide(facts(false, true, CODE, null, 0), sent(CODE, null, BadgeTier.NONE, 0), NOW).getKind());
    }

    @Test
    public void disconnectingOrRepairingDeletesFromTheOldCode()
    {
        BadgePolicy.Shared shared = sent(CODE, "Iron Example", 3, NOW - 1000);
        // Disconnect: no code at all.
        assertAction(BadgePolicy.decide(facts(true, true, "", "Iron Example", 3), shared, NOW),
            BadgePolicy.Kind.DELETE, CODE, null, BadgeTier.NONE);
        // Re-paired: down from the old code first, then up under the new one.
        assertAction(BadgePolicy.decide(facts(true, true, OTHER_CODE, "Iron Example", 3), shared, NOW),
            BadgePolicy.Kind.DELETE, CODE, null, BadgeTier.NONE);
        assertAction(BadgePolicy.decide(facts(true, true, OTHER_CODE, "Iron Example", 3), BadgePolicy.Shared.NONE,
            NOW), BadgePolicy.Kind.POST, OTHER_CODE, "Iron Example", 3);
    }

    /** Without online sync's consent nothing goes to the relay, a removal included. */
    @Test
    public void withoutOnlineSyncEvenARemovalWaits()
    {
        BadgePolicy.Shared shared = sent(CODE, "Iron Example", 3, NOW - 1000);
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(false, false, CODE, "Iron Example", 3), shared, NOW).getKind());
        assertEquals(BadgePolicy.Kind.NOTHING,
            BadgePolicy.decide(facts(true, false, "", "Iron Example", 3), shared, NOW).getKind());
    }
}
