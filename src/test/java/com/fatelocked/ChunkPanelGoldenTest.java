package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.panel.ChunkPanelViewModelFactory;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * B1: the sidebar's chunk card against the web app's golden bundles. On the
 * bound character it shows the tracker's entry and every row it lists; on
 * another character it shows Unknown, says why, and lists nothing.
 */
@RunWith(Parameterized.class)
public class ChunkPanelGoldenTest
{
    private static final Gson GSON = new Gson();
    /** The categories the card shows, in order: every category the goldens use. */
    private static final List<String> SHOWN = Arrays.asList(
        "SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT", "TRAVEL", "FARMING", "ACTIVITIES");

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        return GoldenBundleContractTest.scenarios();
    }

    private final String id;
    private final JsonObject chunks;
    private final RulesSnapshot rules;
    private final String account;
    private final ChunkPanelViewModelFactory factory = new ChunkPanelViewModelFactory();

    public ChunkPanelGoldenTest(String id) throws IOException
    {
        this.id = id;
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        JsonObject wire = GSON.fromJson(json, JsonObject.class);
        chunks = wire.getAsJsonObject("rules").getAsJsonObject("chunks");
        rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        JsonElement bound = wire.getAsJsonObject("rules").get("account");
        account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
    }

    @Test
    public void theCardShowsTheTrackersAnswerAndRows()
    {
        DecisionService trusted = DecisionService.create(rules, account, account);
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : chunks.entrySet())
        {
            JsonObject snapshot = entry.getValue().getAsJsonObject();
            ChunkPanelViewModel view = factory.create(
                trusted, GoldenBundleContractTest.chunk(entry.getKey()), null);
            String want = snapshot.get("entry").getAsString() + " " + counts(snapshot);
            String got = view.getEntryStatus() + " " + view.getAllowedCount() + "/" + view.getNotReadyCount()
                + "/" + view.getLockedCount() + "/" + view.getUnknownCount();
            if (!want.equals(got) || view.getTrustReason() != null) mismatches.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertTrue(id + " has rules", chunks.size() > 600);
        assertEquals(id, List.of(), mismatches);
    }

    @Test
    public void anotherCharacterSeesUnknownWithTheReasonAndTheSameNames()
    {
        if (account == null) return;
        DecisionService trusted = DecisionService.create(rules, account, account);
        DecisionService other = DecisionService.create(rules, account, "someone else");
        List<String> mismatches = new ArrayList<>();
        for (String key : chunks.keySet())
        {
            CanonicalChunk chunk = GoldenBundleContractTest.chunk(key);
            ChunkPanelViewModel mine = factory.create(trusted, chunk, null);
            ChunkPanelViewModel theirs = factory.create(other, chunk, null);
            if (theirs.getEntryStatus() != PermissionStatus.UNKNOWN
                || !theirs.getCategories().isEmpty()
                || !"Wrong account".equals(theirs.getStatusNote())
                || !mine.getName().equals(theirs.getName())
                || !String.valueOf(mine.getRegion()).equals(String.valueOf(theirs.getRegion())))
            {
                mismatches.add(key);
            }
        }
        assertEquals(id, List.of(), mismatches);
    }

    /** Allowed/not ready/locked/unknown rows in the categories the card shows. */
    private static String counts(JsonObject snapshot)
    {
        int[] counts = new int[4];
        JsonObject categories = snapshot.getAsJsonObject("categories");
        for (String category : categories == null ? List.<String>of() : categories.keySet())
        {
            assertTrue("the card shows " + category, SHOWN.contains(category));
            for (JsonElement row : (JsonArray) categories.get(category))
            {
                switch (row.getAsJsonObject().get("status").getAsString().toUpperCase(Locale.ROOT))
                {
                    case "ALLOWED": counts[0]++; break;
                    case "NOT_READY": counts[1]++; break;
                    case "LOCKED": counts[2]++; break;
                    default: counts[3]++; break;
                }
            }
        }
        return counts[0] + "/" + counts[1] + "/" + counts[2] + "/" + counts[3];
    }
}
