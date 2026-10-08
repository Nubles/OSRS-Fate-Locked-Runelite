package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.UnlockNews;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What a sync opened (the owner's call, 8 Oct), between the golden vanilla-mid run and the same
 * run a roll later: only for the same run and character, so a start-up, another run or another
 * character never announces anything.
 */
public class UnlockNewsTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);

    @Test
    public void aRollLaterNamesWhatOpenedAreasFirst() throws Exception
    {
        UnlockNews news = UnlockNews.between(mid(rules -> { }), mid(UnlockNewsTest::aRollLater));

        assertEquals("Seers' Village", news.getNames().get(0));
        assertTrue(news.getNames().toString(), news.getNames().contains("Tal Teklan bank"));
        assertTrue(news.getNames().toString(), news.getNames().contains("Woodcutting tier 3"));
        assertTrue(news.getNames().toString(), news.getNames().contains("Axe Shops"));
        assertTrue(news.getChunks().contains(SEERS));
        assertEquals("Seers' Village and " + (news.getNames().size() - 1) + " more", news.headline());
        assertTrue(news.line(), news.line().startsWith("Unlocked Seers' Village, "));
    }

    @Test
    public void theSameRulesOpenNothing() throws Exception
    {
        assertTrue(UnlockNews.between(mid(rules -> { }), mid(rules -> { })).isEmpty());
    }

    @Test
    public void anotherRunAnotherCharacterOrNoRulesAnnounceNothing() throws Exception
    {
        DecisionService before = mid(rules -> { });
        assertSame(UnlockNews.NONE, UnlockNews.between(before, mid(rules -> {
            aRollLater(rules);
            rules.addProperty("runId", "another-run");
        })));
        assertSame(UnlockNews.NONE, UnlockNews.between(before, playing(mid(UnlockNewsTest::aRollLater), "zezima")));
        assertSame(UnlockNews.NONE, UnlockNews.between(
            DecisionService.create(RulesSnapshot.empty(), null, null), mid(UnlockNewsTest::aRollLater)));
    }

    @Test
    public void onlyChunksSayHowMany()
    {
        UnlockNews one = new UnlockNews(java.util.Collections.emptyList(), java.util.Collections.singleton(SEERS));
        assertEquals("A new chunk", one.headline());
        assertEquals("Unlocked a new chunk.", one.line());
    }

    /** Seers' Village rolled, its chunk open, a bank, a Woodcutting tier and the axe shops. */
    private static void aRollLater(JsonObject rules)
    {
        rules.getAsJsonObject("unlocks").getAsJsonArray("regions").add("Seers' Village");
        rules.getAsJsonObject("unlocks").getAsJsonObject("skills").addProperty("Woodcutting", 3);
        JsonArray merchants = new JsonArray();
        merchants.add("Axe Shops");
        rules.getAsJsonObject("unlocks").add("merchants", merchants);
        rules.getAsJsonObject("banks").getAsJsonObject("4912").addProperty("status", "ALLOWED");
        rules.getAsJsonObject("chunks").getAsJsonObject("42,54").addProperty("entry", "ALLOWED");
        rules.getAsJsonObject("chunkEntries").addProperty("42,54", "ALLOWED");
    }

    private static DecisionService mid(Consumer<JsonObject> change) throws Exception
    {
        JsonObject bundle = GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")),
            JsonObject.class);
        JsonObject rules = bundle.getAsJsonObject("rules");
        change.accept(rules);
        // Older readers take the areas from the top level too; keep the two the same.
        bundle.add("unlockedRegions", rules.getAsJsonObject("unlocks").getAsJsonArray("regions").deepCopy());
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, GSON.toJson(bundle))),
            "iron example", "iron example");
    }

    private static DecisionService playing(DecisionService rules, String player)
    {
        return DecisionService.create(rules.rules(), "iron example", player);
    }
}
