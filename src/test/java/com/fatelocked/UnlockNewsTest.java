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

    /** Audit: a bank whose name already says bank, chest or deposit isn't called a "bank bank". */
    @Test
    public void aBankThatNamesItselfIsNotABankBank() throws Exception
    {
        UnlockNews news = UnlockNews.between(mid(rules -> { }), mid(rules -> {
            for (String bank : new String[] { "7227", "6712", "5678", "4912" })
            {
                rules.getAsJsonObject("banks").getAsJsonObject(bank).addProperty("status", "ALLOWED");
            }
        }));

        assertEquals(java.util.Arrays.asList("Piscarilius Bank", "Hosidius Kitchen bank chest",
            "Aldarin dock deposit box", "Tal Teklan bank").stream().sorted().collect(java.util.stream.Collectors.toList()),
            news.getNames().stream().sorted().collect(java.util.stream.Collectors.toList()));
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

    /** Chunked: an area opens with any one of its chunks, so the news names the chunk instead. */
    @Test
    public void aChunkedRollNamesTheChunkNotItsArea() throws Exception
    {
        UnlockNews news = UnlockNews.between(chunkedWalk(rules -> { }), chunkedWalk(UnlockNewsTest::aChunkLater));

        assertEquals(java.util.Collections.singleton(CRAFTING_GUILD), news.getChunks());
        assertEquals("Crafting Guild chunk", news.getNames().get(0));
        assertTrue(news.getNames().toString(), news.getNames().stream().noneMatch(name -> name.startsWith("Falador")));
        assertEquals("Unlocked Crafting Guild chunk.", news.line());
    }

    /**
     * Audit: Sailing opens the sea the tracker snapshots, but the sea isn't land, so the news
     * names no sea chunks one by one; only the land that opened with it, and the tier.
     */
    @Test
    public void openingTheSeaNamesNoSeaChunks() throws Exception
    {
        UnlockNews news = UnlockNews.between(sameRun("chunked-fresh"), sameRun("chunked-sailing"));

        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(new CanonicalChunk(49, 49), new CanonicalChunk(49, 50))),
            news.getChunks());
        assertEquals(java.util.Arrays.asList("Lumbridge Castle Backyard chunk", "Sailing tier 1",
                "West Lumbridge Swamp chunk"),
            news.getNames().stream().sorted().collect(java.util.stream.Collectors.toList()));

        UnlockNews vanilla = UnlockNews.between(sameRun("vanilla-fresh"), sameRun("vanilla-sailing"));
        java.util.Set<CanonicalChunk> falador = new java.util.HashSet<>();
        for (String key : new String[] { "45,52", "46,49", "46,51", "46,52", "46,53", "47,49", "47,50", "47,51",
            "47,52", "47,53" })
        {
            falador.add(GoldenBundleContractTest.chunk(key));
        }
        assertEquals("the land that opened glows, not the sea", falador, vanilla.getChunks());
        assertTrue(vanilla.getNames().toString(), vanilla.getNames().contains("Sailing tier 1"));
        assertTrue(vanilla.getNames().toString(),
            vanilla.getNames().stream().noneMatch(name -> name.matches("\\d+,\\d+ chunk")));
    }

    /** A golden as one run, so two of them read as the same run a sync later. */
    private static DecisionService sameRun(String id) throws Exception
    {
        JsonObject bundle = GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")), JsonObject.class);
        bundle.addProperty("runId", "same-run");
        bundle.getAsJsonObject("rules").addProperty("runId", "same-run");
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, GSON.toJson(bundle))),
            "iron example", "iron example");
    }

    private static final CanonicalChunk CRAFTING_GUILD = new CanonicalChunk(45, 51);

    /** The chunked-walk run a roll later: 45,51 (the Crafting Guild) unlocked. */
    private static void aChunkLater(JsonObject bundle)
    {
        JsonObject rules = bundle.getAsJsonObject("rules");
        rules.getAsJsonObject("unlocks").getAsJsonArray("chunks").add("45,51");
        bundle.getAsJsonArray("unlockedChunks").add("45,51");
        rules.getAsJsonObject("chunks").getAsJsonObject("45,51").addProperty("entry", "ALLOWED");
        rules.getAsJsonObject("chunkEntries").addProperty("45,51", "ALLOWED");
    }

    private static DecisionService chunkedWalk(Consumer<JsonObject> change) throws Exception
    {
        JsonObject bundle = GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("chunked-walk.bundle.json.gz")),
            JsonObject.class);
        change.accept(bundle);
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, GSON.toJson(bundle))),
            "iron example", "iron example");
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
