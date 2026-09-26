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
import static org.junit.Assert.assertTrue;

/**
 * B11: Slayer tasks through the decision service. A task is LOCKED only
 * when every chunk its monsters live in is locked, and a known master's own
 * list decides first: in vanilla-mid, abyssal demons are owned for most
 * masters but Krystilia's are all in the locked Wilderness.
 */
public class SlayerDecisionTest
{
    private static final Gson GSON = new Gson();

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

    /** Every task key in every golden bundle: owned anywhere, else locked everywhere, else unknown. */
    @Test
    public void everyGoldenTaskFollowsItsChunks() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            JsonObject wire = GSON.fromJson(json, JsonObject.class);
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

    private static DecisionService playing(String id) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", "iron example");
    }
}
