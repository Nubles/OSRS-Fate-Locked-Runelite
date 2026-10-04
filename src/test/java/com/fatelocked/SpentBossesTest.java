package com.fatelocked;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A boss with no Standard Keys left: in Vanilla the tracker refuses its kills, so RuneLite neither
 * records them nor offers them. vanilla-mid has Brutus and the Theatre spent, and Zulrah with a Key
 * left; xtreme-varrock rolls every kill.
 */
public class SpentBossesTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyGoldenReadsTheTrackersSpentBosses() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            List<String> want = new ArrayList<>();
            for (JsonElement boss : GoldenBundleContractTest.json(id + ".expect.json").getAsJsonArray("spentBosses"))
            {
                want.add(boss.getAsString());
            }
            assertEquals(id, want, golden(id).getRules().getSpentBosses());
        }
        assertEquals(Arrays.asList("Brutus", "Theatre of Blood"), golden("vanilla-mid").getRules().getSpentBosses());
    }

    @Test
    public void aSpentBossesKillsAndRaidsAreCovered() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        assertTrue(SpentBosses.covers(mid, FateEventType.BOSS_KILL, "Brutus"));
        assertTrue(SpentBosses.covers(mid, FateEventType.BOSS_KILL, " brutus "));
        assertTrue(SpentBosses.covers(mid, FateEventType.RAID_COMPLETION, "Theatre of Blood"));
        assertFalse(SpentBosses.covers(mid, FateEventType.BOSS_KILL, "Zulrah"));
        // Only kills: anything else with the same name still rolls.
        assertFalse(SpentBosses.covers(mid, FateEventType.COLLECTION_LOG, "Brutus"));
        assertFalse(SpentBosses.covers(mid, FateEventType.BOSS_KILL, null));
        assertFalse(SpentBosses.covers(null, FateEventType.BOSS_KILL, "Brutus"));
        // A mode that rolls every kill spends none.
        assertFalse(SpentBosses.covers(golden("xtreme-varrock"), FateEventType.BOSS_KILL, "Brutus"));
    }

    /** Rules from before the list, or with a list that isn't one, spend no boss. */
    @Test
    public void olderOrMalformedRulesSpendNone() throws Exception
    {
        JsonObject withoutCapability = wire("vanilla-mid");
        JsonArray capabilities = new JsonArray();
        for (JsonElement capability : withoutCapability.getAsJsonObject("rules").getAsJsonArray("capabilities"))
        {
            if (!"spentBosses".equals(capability.getAsString())) capabilities.add(capability);
        }
        withoutCapability.getAsJsonObject("rules").add("capabilities", capabilities);
        JsonObject notAList = wire("vanilla-mid");
        notAList.getAsJsonObject("rules").add("spentBosses", new JsonPrimitive("Brutus"));

        for (JsonObject older : Arrays.asList(withoutCapability, notAList))
        {
            FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON, older.toString());
            assertNull(bundle.getRules().getSpentBosses());
            assertFalse(SpentBosses.covers(bundle, FateEventType.BOSS_KILL, "Brutus"));
        }

        JsonObject mixed = wire("vanilla-mid");
        JsonArray bosses = new JsonArray();
        bosses.add(7);
        bosses.add("Brutus");
        mixed.getAsJsonObject("rules").add("spentBosses", bosses);
        assertEquals(Arrays.asList("Brutus"),
            FateLockedBundle.loadFromJson(GSON, mixed.toString()).getRules().getSpentBosses());
    }

    @Test
    public void theRollInboxLeavesOutASpentBossesKills() throws Exception
    {
        List<DetectedEventStore.Entry> offered = Arrays.asList(
            entry("1", FateEventType.BOSS_KILL, "Brutus"),
            entry("2", FateEventType.BOSS_KILL, "Zulrah"),
            entry("3", FateEventType.QUEST, "Dragon Slayer I"),
            entry("4", FateEventType.RAID_COMPLETION, "Theatre of Blood"));
        assertEquals(Arrays.asList("2", "3"), ids(SpentBosses.without(golden("vanilla-mid"), offered)));
        assertEquals(Arrays.asList("1", "2", "3", "4"), ids(SpentBosses.without(golden("xtreme-varrock"), offered)));
    }

    private static DetectedEventStore.Entry entry(String id, FateEventType type, String label)
    {
        FateEvent event = FateEvent.builder().eventId(id).eventType(type).canonicalLabel(label).build();
        return new DetectedEventStore.Entry(event, DetectedEventStore.Status.NEW);
    }

    private static List<String> ids(List<DetectedEventStore.Entry> entries)
    {
        return entries.stream().map(entry -> entry.getEvent().getEventId()).collect(Collectors.toList());
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }

    private static JsonObject wire(String id) throws Exception
    {
        return GSON.fromJson(GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")),
            JsonObject.class);
    }
}
