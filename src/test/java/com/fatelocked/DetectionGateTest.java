package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.runelite.api.WorldType;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DetectionGateTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void countsForTheBoundCharacterOnANormalWorld() throws Exception
    {
        FateLockedBundle rules = rulesBoundTo("Nubles");

        assertTrue(DetectionGate.allows(rules, "Nubles", EnumSet.of(WorldType.MEMBERS)));
        assertTrue(DetectionGate.allows(rules, " nubles ", EnumSet.noneOf(WorldType.class)));
        // PvP and high-risk worlds save to the account like any other.
        assertTrue(DetectionGate.allows(rules, "Nubles",
            EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
    }

    @Test
    public void notForAnotherCharacterOrAnUnboundProfile() throws Exception
    {
        assertFalse(DetectionGate.allows(rulesBoundTo("Nubles"), "Zezima",
            EnumSet.of(WorldType.MEMBERS)));
        assertFalse(DetectionGate.allows(rulesBoundTo("Nubles"), null,
            EnumSet.of(WorldType.MEMBERS)));
        assertFalse(DetectionGate.allows(rulesBoundTo(null), "Nubles",
            EnumSet.of(WorldType.MEMBERS)));
    }

    @Test
    public void notOnAWorldWhoseProgressIsNotTheAccountsOwn() throws Exception
    {
        FateLockedBundle rules = rulesBoundTo("Nubles");
        for (WorldType other : EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN,
            WorldType.TOURNAMENT_WORLD, WorldType.BETA_WORLD, WorldType.NOSAVE_MODE,
            WorldType.FRESH_START_WORLD, WorldType.QUEST_SPEEDRUNNING,
            WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA))
        {
            assertFalse(other.name(), DetectionGate.allows(rules, "Nubles",
                EnumSet.of(WorldType.MEMBERS, other)));
        }
    }

    @Test
    public void notWithoutRulesForARun()
    {
        assertFalse(DetectionGate.allows(null, "Nubles", EnumSet.of(WorldType.MEMBERS)));
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
