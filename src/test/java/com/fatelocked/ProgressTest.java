package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B10 and E5: unlock progress in the HUD and the infobox comes from the
 * decision service, as the tracker counts it (R9): vanilla-mid shows 15/187
 * areas, as its run card does, where the plugin used to count 13/177.
 * Another character sees none.
 */
public class ProgressTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyGoldenShowsTheTrackersProgress() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            JsonObject want = GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("progress");
            JsonObject chunks = want.getAsJsonObject("chunks");
            assertEquals(id, new Progress(want.get("unit").getAsString(), want.get("unlocked").getAsInt(),
                want.get("total").getAsInt(), chunks.get("unlocked").getAsInt(), chunks.get("total").getAsInt()),
                trusted(golden(id)).progress());
        }
        Progress mid = trusted(golden("vanilla-mid")).progress();
        assertEquals(new Progress(Progress.AREAS, 15, 187, mid.getChunksUnlocked(), 624), mid);
    }

    /** Without the tracker's progress, or with a malformed one, today's counts from the area lists. */
    @Test
    public void olderRulesKeepTodaysCounts() throws Exception
    {
        JsonObject withoutCapability = wire("vanilla-mid");
        JsonArray capabilities = new JsonArray();
        for (JsonElement capability : withoutCapability.getAsJsonObject("rules").getAsJsonArray("capabilities"))
        {
            if (!"progress".equals(capability.getAsString())) capabilities.add(capability);
        }
        withoutCapability.getAsJsonObject("rules").add("capabilities", capabilities);
        JsonObject beyondTotal = wire("vanilla-mid");
        beyondTotal.getAsJsonObject("rules").getAsJsonObject("progress").addProperty("unlocked", 188);
        JsonObject unknownUnit = wire("vanilla-mid");
        unknownUnit.getAsJsonObject("rules").getAsJsonObject("progress").addProperty("unit", "regions");
        JsonObject fraction = wire("vanilla-mid");
        fraction.getAsJsonObject("rules").getAsJsonObject("progress").getAsJsonObject("chunks").addProperty("unlocked", 4.5);
        JsonObject notAnObject = wire("vanilla-mid");
        notAnObject.getAsJsonObject("rules").add("progress", new JsonPrimitive("15/187"));

        for (JsonObject older : Arrays.asList(withoutCapability, beyondTotal, unknownUnit, fraction, notAnObject))
        {
            FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON, older.toString());
            assertEquals(new Progress(Progress.AREAS, 13, 177, bundle.getUnlockedChunks(), bundle.getTotalChunks()),
                trusted(bundle).progress());
        }

        // An older Chunked export counts its chunks.
        JsonObject walk = wire("chunked-walk");
        walk.getAsJsonObject("rules").remove("progress");
        Progress chunked = trusted(FateLockedBundle.loadFromJson(GSON, walk.toString())).progress();
        assertEquals(Progress.CHUNKS, chunked.getUnit());
        assertEquals(chunked.getChunksUnlocked() + " of " + chunked.getChunksTotal() + " chunks unlocked",
            ProgressText.infoBoxTooltip(chunked));
    }

    /** A Chunked run counts chunks. */
    @Test
    public void aChunkedRunCountsChunks() throws Exception
    {
        Progress walk = trusted(golden("chunked-walk")).progress();
        assertEquals(new Progress(Progress.CHUNKS, 7, 624, 7, 624), walk);
        assertEquals("7/624 · 1%", ProgressText.hudLine(walk));
        assertEquals("7 of 624 chunks unlocked", ProgressText.infoBoxTooltip(walk));
    }

    @Test
    public void theHudAndInfoBoxShowItOnlyForTheRulesCharacter() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        Progress progress = trusted(mid).progress();
        String percent = progress.percent() + "%";

        assertEquals("15/187 · " + percent, ProgressText.hudLine(progress));
        assertEquals(percent, ProgressText.infoBoxText(progress));
        assertEquals("15 of 187 areas unlocked</br>" + progress.getChunksUnlocked() + " of "
            + progress.getChunksTotal() + " chunks", ProgressText.infoBoxTooltip(progress));

        DecisionService other = DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else");
        assertNull(other.progress());
        assertNull(ProgressText.hudLine(null));
        assertNull("no box", ProgressText.infoBoxText(null));
        assertNull(ProgressText.infoBoxTooltip(null));
        assertNull("no chunks, no line", ProgressText.hudLine(new Progress(Progress.AREAS, 0, 0, 0, 0)));
        assertNull("no chunks, no box", ProgressText.infoBoxText(new Progress(Progress.AREAS, 0, 0, 0, 0)));
        assertEquals(-1, new Progress(Progress.AREAS, 0, 0, 0, 0).percent());
    }

    /** E6: the HUD's "Unlocked" line is Detailed's, from the decision service, for the rules' character only. */
    @Test
    public void theDetailedHudShowsTheLine() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        CanonicalChunk lumbridge = new CanonicalChunk(50, 50);
        DecisionService mine = trusted(mid);

        assertEquals(ProgressText.hudLine(mine.progress()), HudPresenterTest.lines(HudPresenter.present(
            HudPresenterTest.facts(mine, lumbridge).mode(FateLockedConfig.HudMode.DETAILED).build())).get("Unlocked"));
        assertFalse("not in Compact", HudPresenterTest.lines(HudPresenter.present(
            HudPresenterTest.facts(mine, lumbridge).build())).containsKey("Unlocked"));
        DecisionService other = DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else");
        assertFalse(HudPresenterTest.lines(HudPresenter.present(
            HudPresenterTest.facts(other, lumbridge).mode(FateLockedConfig.HudMode.DETAILED).build()))
            .containsKey("Unlocked"));
    }

    private static DecisionService trusted(FateLockedBundle bundle)
    {
        return DecisionService.create(RulesSnapshot.of(bundle), null, null);
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }

    private static JsonObject wire(String id) throws Exception
    {
        return GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")), JsonObject.class);
    }
}
