package com.fatelocked;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * B11 and E6: Slayer tasks through the decision service. Under the
 * slayerTasks capability the tracker decides each task per master (R16).
 * For older rules a task is LOCKED only when every chunk its monsters live
 * in is locked, and a known master's own list decides first: in
 * vanilla-mid, abyssal demons are owned for most masters but Krystilia's
 * are all in the locked Wilderness.
 */
public class SlayerDecisionTest
{
    private static final Gson GSON = new Gson();

    /** Every task key in every golden bundle reads the tracker's pinned status. */
    @Test
    public void everyGoldenTaskIsTheTrackers() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            DecisionService service = DecisionService.create(
                RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), null, null);
            JsonObject pinned = GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("slayer");
            List<String> mismatches = new ArrayList<>();
            for (Map.Entry<String, JsonElement> task : pinned.entrySet())
            {
                String[] asked = asAsked(task.getKey());
                PermissionStatus got = service.slayerTask(asked[0], asked[1], asked[2]).getStatus();
                if (!task.getValue().getAsString().equals(got.name())) mismatches.add(task.getKey() + " want " + task.getValue() + " got " + got);
            }
            assertTrue(id + " has tasks", pinned.size() > 500);
            assertEquals(id, List.of(), mismatches);
        }
    }

    /**
     * A task key as a game message would give it: master, task and place.
     * A key drops one trailing "s", so one is added back where it ends.
     */
    private static String[] asAsked(String key)
    {
        String master = key.contains(":") ? key.substring(0, key.indexOf(':')) : null;
        String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        String location = null;
        if (name.contains(" - "))
        {
            location = name.substring(name.indexOf(" - ") + 3) + "s";
            name = name.substring(0, name.indexOf(" - "));
        }
        else
        {
            name = name + "s";
        }
        return new String[] { master, name, location };
    }

    @Test
    public void theTrackerDecidesPerMasterWithItsReason() throws Exception
    {
        DecisionService interiors = trackerPlaying("vanilla-interiors");

        assertEquals(new Decision(PermissionStatus.LOCKED, "bears", "Area locked", Decision.Source.SLAYER),
            interiors.slayerTask("krystilia", "bears", null));
        assertEquals("anyone else's bears", PermissionStatus.ALLOWED, interiors.slayerTask(null, "bears", null).getStatus());
        assertEquals("a master with none of their own falls back to anyone's",
            PermissionStatus.ALLOWED, interiors.slayerTask("mortimer", "bears", null).getStatus());
        assertEquals(new Decision(PermissionStatus.LOCKED, "aberrant spectres", "Master: Mount Karuulm", Decision.Source.SLAYER),
            interiors.slayerTask(SlayerAssignment.KONAR, "aberrant spectres", "Catacombs of Kourend"));
        assertEquals("a task the tracker doesn't list falls back to the chunks",
            PermissionStatus.UNKNOWN, interiors.slayerTask(null, "not a monster", null).getStatus());
    }

    @Test
    public void theTrackersTasksAreReadLeniently() throws Exception
    {
        JsonObject json;
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v4-rules.json"))
        {
            json = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        }
        json.add("slayerChunks", GSON.fromJson("{\"spider\": [{\"cx\": 50, \"cy\": 50}]}", JsonObject.class));
        json.getAsJsonObject("rules").add("slayerTasks", GSON.fromJson(
            "{\"krystilia:spider\": {\"status\": \"A_NEWER_STATUS\"}, \"goblin\": {\"reason\": \"no status\"},"
                + " \"spider\": \"not a task\"}", JsonObject.class));
        JsonObject named = json.deepCopy();
        named.getAsJsonObject("rules").add("capabilities", GSON.fromJson("[\"slayerTasks\"]", JsonArray.class));
        DecisionService nubles = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, named.toString())), "nubles", "nubles");

        assertEquals(PermissionStatus.UNKNOWN, nubles.slayerTask("krystilia", "spiders", null).getStatus());
        assertEquals(Decision.Source.SLAYER, nubles.slayerTask("krystilia", "spiders", null).getSource());
        assertEquals("a skipped task falls back to its chunks", PermissionStatus.ALLOWED, nubles.slayerTask(null, "spiders", null).getStatus());
        assertEquals("a task without a status is skipped", Decision.Source.UNMAPPED, nubles.slayerTask(null, "goblins", null).getSource());

        DecisionService older = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json.toString())), "nubles", "nubles");
        assertEquals("without the capability, the chunks decide", PermissionStatus.ALLOWED,
            older.slayerTask("krystilia", "spiders", null).getStatus());
    }

    @Test
    public void aKnownMastersOwnListDecides() throws Exception
    {
        DecisionService mid = playing("vanilla-mid");

        assertEquals(PermissionStatus.ALLOWED, mid.slayerTask(null, "abyssal demons", null).getStatus());
        Decision krystilia = mid.slayerTask("krystilia", "abyssal demons", null);
        assertEquals(PermissionStatus.LOCKED, krystilia.getStatus());
        assertEquals(Decision.Source.SLAYER, krystilia.getSource());
        assertEquals("a master with no list of their own falls back to the merged list",
            PermissionStatus.ALLOWED, mid.slayerTask("mortimer", "abyssal demons", null).getStatus());
        assertEquals(PermissionStatus.ALLOWED, mid.slayerTask(null, "goblins", null).getStatus());
    }

    /** A master's own list decides even when it located nothing: other masters' places don't count. */
    @Test
    public void anEmptyListOfTheMastersOwnIsUnknown() throws Exception
    {
        JsonObject json;
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v4-rules.json"))
        {
            json = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        }
        json.add("slayerChunks", GSON.fromJson(
            "{\"krystilia:spider\": [], \"spider\": [{\"cx\": 50, \"cy\": 50}]}", JsonObject.class));
        DecisionService nubles = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json.toString())), "nubles", "nubles");

        assertEquals(PermissionStatus.ALLOWED, nubles.slayerTask(null, "spiders", null).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, nubles.slayerTask("krystilia", "spiders", null).getStatus());
    }

    @Test
    public void konarsPlaceDecides() throws Exception
    {
        DecisionService mid = playing("vanilla-mid");

        for (String place : new String[] { "Catacombs of Kourend", "Slayer Tower", "Stronghold Slayer Cave" })
        {
            assertEquals(place, PermissionStatus.LOCKED,
                mid.slayerTask(SlayerAssignment.KONAR, "aberrant spectres", place).getStatus());
        }
    }

    @Test
    public void unknownTasksAndOtherCharactersGetNoAnswer() throws Exception
    {
        DecisionService mid = playing("vanilla-mid");
        assertEquals(PermissionStatus.UNKNOWN, mid.slayerTask(null, "not a monster", null).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, mid.slayerTask(null, " ", null).getStatus());

        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"));
        DecisionService other = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", "someone else");
        Decision theirs = other.slayerTask("krystilia", "abyssal demons", null);
        assertEquals(PermissionStatus.UNKNOWN, theirs.getStatus());
        assertEquals(Decision.Source.TRUST, theirs.getSource());
    }

    /** Konar's place finds its task as the game names it, and as the tracker wrote the key, its s dropped. */
    @Test
    public void aTaskKeyReadBackFindsItself()
    {
        Map<String, Integer> keyed = Map.of("konar quo maten:abyssal demons - abys", 1);
        assertEquals(Integer.valueOf(1), FateLockedBundle.atSlayerKey(keyed, "Konar quo Maten:Abyssal demons - Abyss"));
        assertEquals(Integer.valueOf(1), FateLockedBundle.atSlayerKey(keyed, "konar quo maten:abyssal demons - abys"));
        assertNull(FateLockedBundle.atSlayerKey(keyed, "konar quo maten:abyssal demons - aby"));
        assertNull(FateLockedBundle.atSlayerKey(keyed, " "));
    }

    /** Older rules, every task key in every golden bundle: owned anywhere, else locked everywhere, else unknown. */
    @Test
    public void everyGoldenTaskFollowsItsChunks() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            JsonObject wire = withoutSlayerTasks(id);
            String json = wire.toString();
            JsonObject chunks = wire.getAsJsonObject("rules").getAsJsonObject("chunks");
            DecisionService service = DecisionService.create(
                RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), null, null);
            List<String> mismatches = new ArrayList<>();
            int locked = 0;
            for (Map.Entry<String, JsonElement> task : wire.getAsJsonObject("slayerChunks").entrySet())
            {
                String key = task.getKey();
                String master = key.contains(":") ? key.substring(0, key.indexOf(':')) : null;
                String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
                String location = null;
                if (name.contains(" - "))
                {
                    location = name.substring(name.indexOf(" - ") + 3);
                    name = name.substring(0, name.indexOf(" - "));
                }
                JsonArray where = (JsonArray) task.getValue();
                // A plain task the index located nowhere falls back to the monster lists.
                if (where.size() == 0 && master == null && location == null) continue;
                PermissionStatus want = expected(where, chunks);
                if (want == PermissionStatus.LOCKED) locked++;
                PermissionStatus got = service.slayerTask(master, name, location).getStatus();
                if (want != got) mismatches.add(key + " want " + want + " got " + got);
            }
            assertTrue(id + " locks some tasks", locked > 0);
            assertEquals(id, List.of(), mismatches);
        }
    }

    private static PermissionStatus expected(JsonArray where, JsonObject chunks)
    {
        if (where.size() == 0) return PermissionStatus.UNKNOWN;
        boolean allLocked = true;
        for (JsonElement element : where)
        {
            JsonObject at = element.getAsJsonObject();
            JsonObject snapshot = chunks.getAsJsonObject(at.get("cx").getAsInt() + "," + at.get("cy").getAsInt());
            String entry = snapshot == null ? "UNKNOWN" : snapshot.get("entry").getAsString();
            if ("ALLOWED".equals(entry) || "NOT_READY".equals(entry)) return PermissionStatus.ALLOWED;
            if (!"LOCKED".equals(entry)) allLocked = false;
        }
        return allLocked ? PermissionStatus.LOCKED : PermissionStatus.UNKNOWN;
    }

    /** The golden's own character, with older rules: the chunks decide (B11). */
    private static DecisionService playing(String id) throws Exception
    {
        String json = withoutSlayerTasks(id).toString();
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", "iron example");
    }

    /** A golden bundle as older rules send it: without the slayerTasks capability. */
    private static JsonObject withoutSlayerTasks(String id) throws Exception
    {
        JsonObject wire = GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")), JsonObject.class);
        JsonArray capabilities = new JsonArray();
        for (JsonElement capability : wire.getAsJsonObject("rules").getAsJsonArray("capabilities"))
        {
            if (!"slayerTasks".equals(capability.getAsString())) capabilities.add(capability);
        }
        wire.getAsJsonObject("rules").add("capabilities", capabilities);
        return wire;
    }

    /** The golden's own character, with the tracker's Slayer decisions. */
    private static DecisionService trackerPlaying(String id) throws Exception
    {
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")))),
            "iron example", "iron example");
    }
}
