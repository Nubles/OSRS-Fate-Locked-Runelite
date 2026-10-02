package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.runelite.api.WorldType;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

import static com.fatelocked.DetectionGate.Detection.OFF;
import static com.fatelocked.DetectionGate.Detection.RECORD;
import static com.fatelocked.DetectionGate.Detection.RECORD_AND_REMIND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DetectionGateTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void recordsAndRemindsForTheBoundCharacterOnANormalWorld() throws Exception
    {
        FateLockedBundle rules = rulesBoundTo("Nubles");

        assertEquals(RECORD_AND_REMIND, DetectionGate.decide(rules, "Nubles", EnumSet.of(WorldType.MEMBERS)));
        assertEquals(RECORD_AND_REMIND, DetectionGate.decide(rules, " nubles ", EnumSet.noneOf(WorldType.class)));
        // PvP and high-risk worlds save to the account like any other.
        assertEquals(RECORD_AND_REMIND, DetectionGate.decide(rules, "Nubles",
            EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
    }

    @Test
    public void recordsForWhoeverIsLoggedInWhileTheRunIsLinkedToNoOneButNeverReminds() throws Exception
    {
        // Stage 4: the Roll inbox copies them, and the paste shows whose they are.
        assertEquals(RECORD, DetectionGate.decide(rulesBoundTo(null), "Nubles", EnumSet.of(WorldType.MEMBERS)));
        assertEquals(RECORD, DetectionGate.decide(rulesBoundTo(null), "Zezima", EnumSet.of(WorldType.MEMBERS)));
        assertTrue(RECORD.records());
        assertFalse(RECORD.reminds());
    }

    @Test
    public void doesNothingForAnotherCharacterOrNoOne() throws Exception
    {
        assertEquals(OFF, DetectionGate.decide(rulesBoundTo("Nubles"), "Zezima", EnumSet.of(WorldType.MEMBERS)));
        assertEquals(OFF, DetectionGate.decide(rulesBoundTo("Nubles"), null, EnumSet.of(WorldType.MEMBERS)));
        assertEquals(OFF, DetectionGate.decide(rulesBoundTo(null), " ", EnumSet.of(WorldType.MEMBERS)));
        assertFalse(OFF.records());
    }

    @Test
    public void doesNothingOnAWorldWhoseProgressIsNotTheAccountsOwn() throws Exception
    {
        for (FateLockedBundle rules : new FateLockedBundle[]{rulesBoundTo("Nubles"), rulesBoundTo(null)})
        {
            for (WorldType other : EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN,
                WorldType.TOURNAMENT_WORLD, WorldType.BETA_WORLD, WorldType.NOSAVE_MODE,
                WorldType.FRESH_START_WORLD, WorldType.QUEST_SPEEDRUNNING,
                WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA))
            {
                assertEquals(other.name(), OFF, DetectionGate.decide(rules, "Nubles",
                    EnumSet.of(WorldType.MEMBERS, other)));
            }
        }
    }

    @Test
    public void doesNothingWithoutRulesForARun()
    {
        assertEquals(OFF, DetectionGate.decide(null, "Nubles", EnumSet.of(WorldType.MEMBERS)));
    }

    /**
     * The accuracy review, P-10: the Roll inbox says why nothing is noticed for the character
     * logged in, and nothing while something is, or while no one is logged in.
     */
    @Test
    public void saysWhyNothingIsNoticed() throws Exception
    {
        EnumSet<WorldType> members = EnumSet.of(WorldType.MEMBERS);
        EnumSet<WorldType> leagues = EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL);
        FateLockedBundle linked = rulesBoundTo("Nubles");
        FateLockedBundle unlinked = rulesBoundTo(null);

        assertEquals("RuneLite notices nothing on this character: your run is linked to Nubles.",
            DetectionGate.quiet(linked, "Zezima", members));
        assertEquals(DetectionGate.NO_RUN, DetectionGate.quiet(null, "Nubles", members));
        assertEquals(DetectionGate.NO_RUN, DetectionGate.quiet(FateLockedBundle.empty(), "Nubles", members));
        assertEquals(DetectionGate.OTHER_WORLD, DetectionGate.quiet(linked, "Nubles", leagues));
        assertEquals(DetectionGate.OTHER_WORLD, DetectionGate.quiet(unlinked, "Zezima", leagues));
        assertNull(DetectionGate.quiet(linked, "Nubles", members));
        assertNull(DetectionGate.quiet(unlinked, "Zezima", members));
        assertNull("logged out", DetectionGate.quiet(linked, null, members));
        assertNull("logged out", DetectionGate.quiet(null, " ", members));

        // It speaks exactly when nothing is recorded for someone logged in.
        for (FateLockedBundle rules : new FateLockedBundle[]{null, linked, unlinked})
        {
            for (String name : new String[]{"Nubles", "Zezima"})
            {
                for (EnumSet<WorldType> world : List.of(members, leagues))
                {
                    assertEquals(name + " " + world, DetectionGate.decide(rules, name, world).records(),
                        DetectionGate.quiet(rules, name, world) == null);
                }
            }
        }
    }

    /** The v4 fixture, bound to this account, or to none. */
    private static FateLockedBundle rulesBoundTo(String account) throws Exception
    {
        JsonObject root;
        try (InputStream in = DetectionGateTest.class.getClassLoader()
            .getResourceAsStream("bundles/v4-rules.json"))
        {
            root = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                JsonObject.class);
        }
        if (account == null)
        {
            root.getAsJsonObject("rules").add("account", JsonNull.INSTANCE);
        }
        else
        {
            root.getAsJsonObject("rules").addProperty("account", account);
        }
        return FateLockedBundle.loadFromJson(GSON, root.toString());
    }
}
