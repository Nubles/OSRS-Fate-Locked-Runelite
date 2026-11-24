package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.fatelocked.sidebar.LockedThings;
import com.fatelocked.sidebar.PointTarget;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * plugin-ui.d3[3]: a minigame's ACTIVITIES row (minigame: true in the export) names a place, not
 * a thing the game shows, so the Here card offers no click on it, the plugin builds no target or
 * thing from its name, and nothing is tagged or outlined by it. A boss row, and every row of a
 * bundle without the field, is as before. The pinned golden bundles don't carry the field yet,
 * so the test adds it to vanilla-mid's Pest Control row (41,40).
 */
public class MinigameRowsGoldenTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk PEST_CONTROL = new CanonicalChunk(41, 40);
    private static final CanonicalChunk VARDORVIS = new CanonicalChunk(17, 53);
    private static final String[] OPTIONS = {"Talk-to", "Attack"};

    @Test
    public void aMinigameRowIsNotPointedAtTaggedOrOutlined() throws Exception
    {
        DecisionService marked = decisions(true);
        HereModel.Row pest = row(marked, PEST_CONTROL, "Pest Control");
        assertFalse("no click on a minigame's row", pest.isPointable());
        assertFalse(labels(marked, PEST_CONTROL).contains("Pest Control"));
        assertNull(LockedThings.of(marked.details(PEST_CONTROL).orElse(null)).find("Pest Control", OPTIONS));

        assertTrue("a boss row still points", row(marked, VARDORVIS, "Vardorvis").isPointable());
        assertTrue(labels(marked, VARDORVIS).contains("Vardorvis"));
        assertNotNull(LockedThings.of(marked.details(VARDORVIS).orElse(null)).find("Vardorvis", OPTIONS));
    }

    @Test
    public void withoutTheFieldEveryRowIsAsBefore() throws Exception
    {
        DecisionService older = decisions(false);
        assertTrue(row(older, PEST_CONTROL, "Pest Control").isPointable());
        assertTrue(labels(older, PEST_CONTROL).contains("Pest Control"));
        assertNotNull(LockedThings.of(older.details(PEST_CONTROL).orElse(null)).find("Pest Control", OPTIONS));
    }

    private static HereModel.Row row(DecisionService decisions, CanonicalChunk chunk, String name)
    {
        return new HerePresenter().present(decisions, chunk).getGroups().stream()
            .filter(group -> group.getCategory().equals("ACTIVITIES")).findFirst()
            .orElseThrow(() -> new AssertionError("no Activities at " + chunk))
            .getRows().stream().filter(row -> row.getName().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no " + name));
    }

    private static List<String> labels(DecisionService decisions, CanonicalChunk chunk)
    {
        return HerePresenter.pointable(decisions, chunk).stream().map(PointTarget::getLabel)
            .collect(Collectors.toList());
    }

    private static DecisionService decisions(boolean markPestControl) throws Exception
    {
        JsonObject wire = GSON.fromJson(GoldenBundleContractTest.gunzip(
            GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")), JsonObject.class);
        JsonObject pest = wire.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("41,40")
            .getAsJsonObject("categories").getAsJsonArray("ACTIVITIES").get(0).getAsJsonObject();
        assertEquals("Pest Control", pest.get("name").getAsString());
        if (markPestControl) pest.addProperty("minigame", true);
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, wire.toString())),
            "iron example", "iron example");
    }
}
