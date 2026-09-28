package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;

/**
 * B5, E8, E6: what the HUD says, from the decision service. On vanilla-mid, Glarial's Tomb
 * reads "Not ready", another character reads "Different character" and none of the other
 * character's locks, and the sea the rules don't cover reads as the rules' entry says. The
 * "Here" label is the tracker's area, and a locked or not-ready place says why, in the
 * tracker's words. Compact and Detailed hold what the plan says, in words.
 */
public class HudPresenterTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk GLARIALS_TOMB = new CanonicalChunk(39, 53);
    private static final CanonicalChunk OCEAN = new CanonicalChunk(40, 41);

    @Test
    public void theStatusLineSaysWhatTheRulesSay() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");

        assertEquals(Arrays.asList("Locked", "BAD", "Unlock Seers' Village"), status(mid, SEERS));
        assertEquals(Arrays.asList("Unlocked", "GOOD", null), status(mid, LUMBRIDGE));
        assertEquals(Arrays.asList("Not ready", "PENDING", HudPresenter.shorten("No route from Lumbridge", 22)),
            status(mid, GLARIALS_TOMB));
        // The sea has no snapshot; the rules' chunk entries lock it without Sailing (R1), and say no more.
        assertEquals(Arrays.asList("Locked", "BAD", null), status(mid, OCEAN));
        assertEquals("a place the rules don't map", Arrays.asList("Uncharted", "NEUTRAL", null),
            status(mid, new CanonicalChunk(1, 1)));
        assertEquals("why, in the status's tone", Palette.Tone.BAD,
            line(HudPresenter.present(facts(mid, SEERS).build()), "Why").getTone());
        assertEquals(Palette.Tone.PENDING, line(HudPresenter.present(facts(mid, GLARIALS_TOMB).build()), "Why").getTone());
    }

    /** Another character sees where they are and that the run isn't theirs; nothing else. */
    @Test
    public void anotherCharacterSeesOnlyThatTheRunIsntTheirs() throws Exception
    {
        DecisionService other = playing("vanilla-mid", "someone else");

        for (CanonicalChunk chunk : Arrays.asList(SEERS, LUMBRIDGE, GLARIALS_TOMB, OCEAN))
        {
            assertEquals(chunk.toString(), Arrays.asList("Different character", "BAD", null), status(other, chunk));
        }
        HudModel all = HudPresenter.present(facts(other, SEERS)
            .strict(StrictModeStatusView.of(true, false, 0, "you're on another character"))
            .slayerWarning("bears").overTier("Weapon").build());
        assertEquals(Arrays.asList("Here", "Status"), labels(all));
        HudModel detailed = HudPresenter.present(facts(other, SEERS).mode(FateLockedConfig.HudMode.DETAILED).build());
        assertEquals(Arrays.asList("Here", "Status"), labels(detailed));
        assertFalse("two short lines need no wider panel", detailed.isDetailed());
    }

    /**
     * Every golden chunk the rules decide shows its entry and the tracker's reason, under the
     * tracker's area: the same name as the old area lists.
     */
    @Test
    public void everyGoldenChunkShowsItsEntryUnderTheSameName() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON, json);
            JsonObject chunks = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").getAsJsonObject("chunks");
            DecisionService trusted = DecisionService.create(RulesSnapshot.of(bundle), null, null);
            DecisionService other = DecisionService.create(RulesSnapshot.of(bundle), "iron example", "someone else");
            List<String> mismatches = new ArrayList<>();
            int reasons = 0;
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet())
            {
                CanonicalChunk chunk = GoldenBundleContractTest.chunk(entry.getKey());
                JsonObject snapshot = entry.getValue().getAsJsonObject();
                String entryStatus = snapshot.get("entry").getAsString();
                String why = "LOCKED".equals(entryStatus) || "NOT_READY".equals(entryStatus)
                    ? string(snapshot, "entryReason") : null;
                String area = string(snapshot, "area");
                String region = string(snapshot, "region");
                String here = area == null ? null : region == null || region.equals(area) ? area : area + " · " + region;
                List<String> want = Arrays.asList(word(entryStatus), tone(entryStatus),
                    why == null ? null : HudPresenter.shorten(why, 22));
                List<String> got = status(trusted, chunk);
                if (why != null) reasons++;
                if (!want.equals(got)
                    || !Objects.equals(here, trusted.areaName(chunk))
                    || !Objects.equals(here, other.areaName(chunk))
                    || !Objects.equals(bundle.labelAt(chunk), trusted.areaName(chunk)))
                {
                    mismatches.add(entry.getKey() + " want " + want + " in " + here
                        + " got " + got + " in " + trusted.areaName(chunk));
                }
            }
            assertTrue(id, chunks.size() > 600);
            assertTrue(id, reasons > 100);
            assertEquals(id, Collections.emptyList(), mismatches);
        }
    }

    /** E8: the tracker's area names a chunk, with its region, ahead of the area lists older rules use. */
    @Test
    public void theTrackersAreaNamesTheChunk() throws Exception
    {
        JsonObject root = GSON.fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject lumbridge = root.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("50,50");
        assertEquals("older rules: the area lists", "Lumbridge · Misthalin", anyone(root).areaName(LUMBRIDGE));

        lumbridge.addProperty("area", "Lumbridge Swamp");
        assertEquals("Lumbridge Swamp · Misthalin", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", "Misthalin");
        assertEquals("Misthalin", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", "Lumbridge Swamp");
        lumbridge.addProperty("region", " ");
        assertEquals("Lumbridge Swamp", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", " ");
        assertEquals("a blank area: the area lists", "Lumbridge · Misthalin", anyone(root).areaName(LUMBRIDGE));
    }

    /** E8: a reason is trimmed, and a blank one is none. */
    @Test
    public void aBlankReasonIsNone() throws Exception
    {
        JsonObject root = GSON.fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject lumbridge = root.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("50,50");
        lumbridge.addProperty("entry", "LOCKED");
        lumbridge.addProperty("entryReason", " Unlock Lumbridge ");
        assertEquals(Arrays.asList("Locked", "BAD", "Unlock Lumbridge"), status(anyone(root), LUMBRIDGE));
        lumbridge.addProperty("entryReason", "  ");
        assertEquals(Arrays.asList("Locked", "BAD", null), status(anyone(root), LUMBRIDGE));
        lumbridge.addProperty("entry", "ALLOWED");
        lumbridge.addProperty("entryReason", "Unlock Lumbridge");
        assertEquals("an unlocked place needs no why", Arrays.asList("Unlocked", "GOOD", null),
            status(anyone(root), LUMBRIDGE));
    }

    /** E8: in an interior the Here label is its name from the rules' places; one with none, and the sea, have none. */
    @Test
    public void anInteriorIsNamedByItsPlace() throws Exception
    {
        DecisionService interiors = playing("vanilla-interiors", "someone else");
        assertEquals("Kurask Lair", interiors.areaName(new CanonicalChunk(18, 143)));
        assertEquals(null, interiors.areaName(new CanonicalChunk(18, 155)));
        assertEquals(null, playing("vanilla-mid", "iron example").areaName(OCEAN));
        assertEquals("a place with no name is named by where it is", "Chunk (18, 155)",
            lines(HudPresenter.present(facts(interiors, new CanonicalChunk(18, 155)).build())).get("Here"));
    }

    /** B16: the HUD's Strict Mode line, from the same status as the sidebar; none when off. */
    @Test
    public void theHudShowsStrictModesStatus() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");

        Map<String, String> paused = lines(HudPresenter.present(facts(mid, LUMBRIDGE)
            .strict(StrictModeStatusView.of(true, true, 42, null)).build()));
        assertEquals("Paused · 42s", paused.get("Strict Mode"));
        HudModel inactive = HudPresenter.present(facts(mid, LUMBRIDGE)
            .strict(StrictModeStatusView.of(true, false, 0, "you are not logged in")).build());
        assertEquals("Inactive", lines(inactive).get("Strict Mode"));
        assertEquals(Palette.Tone.PENDING, line(inactive, "Strict Mode").getTone());
        HudModel active = HudPresenter.present(facts(mid, LUMBRIDGE)
            .strict(StrictModeStatusView.of(true, false, 0, null)).build());
        assertEquals("Active", lines(active).get("Strict Mode"));
        assertEquals(Palette.Tone.GOOD, line(active, "Strict Mode").getTone());
        assertNull(lines(HudPresenter.present(facts(mid, LUMBRIDGE)
            .strict(StrictModeStatusView.of(false, false, 0, null)).build())).get("Strict Mode"));
    }

    /** An older export's Status is its own lock state, as before, and gives no reasons. */
    @Test
    public void anOlderExportShowsItsOwnLockState() throws Exception
    {
        FateLockedBundle v3 = FateLockedBundle.loadFromJson(GSON, fixtureText("bundles/v3-standard.json"));
        DecisionService nubles = DecisionService.create(RulesSnapshot.of(v3), "nubles", "nubles");
        CanonicalChunk falador = new CanonicalChunk(46, 52);

        assertEquals(FateLockedBundle.LockState.UNLOCKED, v3.lockStateAt(falador));
        assertEquals(Arrays.asList("Unlocked", "GOOD", null), status(nubles, falador));
        FateLockedBundle asgarniaLocked = FateLockedBundle.loadFromJson(GSON,
            "{\"version\":3,\"chunks\":{\"Asgarnia\":[{\"cx\":46,\"cy\":52}]},\"unlockedRegions\":[]}");
        DecisionService bare = DecisionService.create(RulesSnapshot.of(asgarniaLocked), null, null);
        assertEquals(Arrays.asList("Locked", "BAD", null), status(bare, falador));
        assertEquals("Falador · Asgarnia", nubles.areaName(falador));
        assertEquals("no banks or shops to point to", Arrays.asList("Here", "Status"),
            labels(HudPresenter.present(facts(nubles, falador).build())));
    }

    /** Rules that count no chunks give no progress line. */
    @Test
    public void noChunksCountedIsNoProgressLine()
    {
        DecisionService uncounted = org.mockito.Mockito.mock(DecisionService.class);
        org.mockito.Mockito.when(uncounted.trust()).thenReturn(Trust.TRUSTED);
        org.mockito.Mockito.when(uncounted.progress()).thenReturn(new Progress(Progress.AREAS, 0, 0, 0, 0));
        assertSame(HudModel.NONE, HudPresenter.present(facts(uncounted, null)
            .mode(FateLockedConfig.HudMode.DETAILED).build()));
    }

    /** No HUD when it's off, without rules, or before the rules' character logs in. */
    @Test
    public void offOrWithoutRulesThereIsNoHud() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");
        assertSame(HudModel.NONE, HudPresenter.present(facts(mid, LUMBRIDGE).mode(FateLockedConfig.HudMode.OFF).build()));
        assertSame(HudModel.NONE, HudPresenter.present(
            facts(DecisionService.create(RulesSnapshot.empty(), null, null), LUMBRIDGE).build()));
        assertSame(HudModel.NONE, HudPresenter.present(
            facts(DecisionService.create(mid.rules(), "iron example", null), LUMBRIDGE).build()));
        assertSame("nothing to say where the chunk can't be found", HudModel.NONE,
            HudPresenter.present(facts(mid, null).build()));
    }

    @Test
    public void ruleWarningsShowInEitherMode() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");
        for (FateLockedConfig.HudMode mode : new FateLockedConfig.HudMode[] {
            FateLockedConfig.HudMode.COMPACT, FateLockedConfig.HudMode.DETAILED})
        {
            HudModel model = HudPresenter.present(facts(mid, LUMBRIDGE).mode(mode)
                .slayerWarning("bears").overTier("Weapon").build());
            assertEquals(mode.name(), "bears", lines(model).get("Slayer"));
            assertEquals(Palette.Tone.BAD, line(model, "Slayer").getTone());
            assertEquals("Weapon", lines(model).get("Over-tier"));
            assertEquals(Palette.Tone.BAD, line(model, "Over-tier").getTone());
        }
    }

    /** E6: Detailed adds progress, the run, and what the place holds; Compact doesn't. */
    @Test
    public void detailedAddsProgressTheRunAndWhatThePlaceHolds() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");
        FateLockedBundle.RunState run = new FateLockedBundle.RunState();
        run.keys = 3;
        run.specialKeys = 1;
        run.fatePoints = 12;
        run.activeBuff = "LUCK";
        run.pinnedGoals = Collections.singletonList("Fire cape");
        List<HereModel.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 7; i++)
        {
            rows.add(new HereModel.Row("Quest " + i, "Can do", Palette.Tone.GOOD, null));
        }
        HereModel here = new HereModel("Lumbridge", "Unlocked", Palette.Tone.GOOD, null, null,
            Collections.emptyList(), Arrays.asList(new HereModel.Group("QUESTS", "Quests", rows),
                new HereModel.Group("SHOPS", "Shops", Arrays.asList(
                    new HereModel.Row("Shop 1", "Locked", Palette.Tone.BAD, null),
                    new HereModel.Row("Shop 2", "Locked", Palette.Tone.BAD, null)))), null);

        HudModel compact = HudPresenter.present(facts(mid, LUMBRIDGE).run(run).here(here).build());
        assertFalse(compact.isDetailed());
        assertEquals(Arrays.asList("Here", "Status", "Bank", "Shop"), labels(compact));

        HudModel detailed = HudPresenter.present(facts(mid, LUMBRIDGE).mode(FateLockedConfig.HudMode.DETAILED)
            .run(run).here(here).build());
        assertTrue(detailed.isDetailed());
        Map<String, String> lines = lines(detailed);
        assertEquals(ProgressText.hudLine(mid.progress()), lines.get("Unlocked"));
        assertEquals("3", lines.get("Keys"));
        assertEquals("1", lines.get("Omni-Keys"));
        assertFalse("none held, no line", lines.containsKey("Chaos Keys"));
        assertEquals("12", lines.get("Fate Points"));
        assertEquals("Ritual of Clarity", lines.get("Ritual"));
        assertEquals(Palette.Tone.GOOD, line(detailed, "Ritual").getTone());
        assertEquals("Fire cape", lines.get("Goal"));
        List<String> labels = labels(detailed);
        int quests = labels.indexOf("Quests");
        assertEquals(Arrays.asList("Quests", "Quest 1", "Quest 2", "Quest 3", "Quest 4", "Quest 5", "+2 more"),
            labels.subList(quests, quests + 7));
        assertNull("a heading has no value, and no tone", line(detailed, "Quests").getTone());
        assertEquals("Can do", lines.get("Quest 1"));
        assertEquals(Palette.Tone.GOOD, line(detailed, "Quest 1").getTone());
        assertEquals(Palette.Tone.NEUTRAL, line(detailed, "+2 more").getTone());
        int shops = labels.indexOf("Shops");
        assertEquals("a short group lists all it holds", Arrays.asList("Shops", "Shop 1", "Shop 2"),
            labels.subList(shops, labels.size()));

        run.specialKeys = 0;
        run.chaosKeys = 2;
        run.activeBuff = null;
        run.pinnedGoals = Collections.emptyList();
        Map<String, String> other = lines(HudPresenter.present(facts(mid, LUMBRIDGE)
            .mode(FateLockedConfig.HudMode.DETAILED).run(run).build()));
        assertFalse("none held, no line", other.containsKey("Omni-Keys"));
        assertEquals("2", other.get("Chaos Keys"));
        assertFalse("no ritual", other.containsKey("Ritual"));
        assertFalse("no goal pinned", other.containsKey("Goal"));
    }

    /** Each line fits the panel: long text is cut, ending in an ellipsis; text that fits is left alone. */
    @Test
    public void longTextIsCutToFitALine() throws Exception
    {
        assertEquals("Lumbr", HudPresenter.shorten("Lumbr", 5));
        assertEquals("Lumb…", HudPresenter.shorten("Lumbri", 5));

        DecisionService mid = playing("vanilla-mid", "iron example");
        String seers = mid.areaName(SEERS);
        assertTrue(seers, seers.length() > 22);
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        FateLockedBundle.RunState run = new FateLockedBundle.RunState();
        run.pinnedGoals = Collections.singletonList(alphabet);
        HereModel here = new HereModel("Seers' Village", "Locked", Palette.Tone.BAD, null, null,
            Collections.emptyList(), Collections.singletonList(new HereModel.Group("QUESTS", "Quests",
                Collections.singletonList(new HereModel.Row(alphabet, "Locked", Palette.Tone.BAD, null)))), null);
        Map<String, String> lines = lines(HudPresenter.present(facts(mid, SEERS).mode(FateLockedConfig.HudMode.DETAILED)
            .slayerWarning(alphabet).overTier(alphabet).run(run).here(here).build()));

        assertEquals(seers.substring(0, 21) + "…", lines.get("Here"));
        assertEquals("abcdefghijklmnopq…", lines.get("Slayer"));
        assertEquals("abcdefghijklmnopqrs…", lines.get("Over-tier"));
        assertEquals("abcdefghijklmnopqrs…", lines.get("Goal"));
        assertEquals("Locked", lines.get("abcdefghijklmnopq…"));
    }

    /** The nearest bank and shop: here is good, none is bad, and the way there is plain. */
    @Test
    public void theNearestLinesTakeTheirTones() throws Exception
    {
        DecisionService banks = playing("custom-lumbridge-banks-off", "iron example");
        CanonicalChunk draynor = new CanonicalChunk(48, 50);
        CanonicalChunk west = new CanonicalChunk(46, 50);
        HudModel atDraynor = HudPresenter.present(facts(banks, draynor)
            .bank(banks.nearestBank(draynor)).shop(banks.nearestShop(draynor)).build());
        assertEquals(Palette.Tone.GOOD, line(atDraynor, "Bank").getTone());
        assertEquals(Palette.Tone.BAD, line(atDraynor, "Shop").getTone());
        assertNull(line(HudPresenter.present(facts(banks, west).bank(banks.nearestBank(west)).build()), "Bank").getTone());
    }

    /** The overlay fonts have no ✓ or ⚠: the HUD says everything in words. */
    @Test
    public void wordsNeverMarks() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");
        FateLockedBundle.RunState run = new FateLockedBundle.RunState();
        for (CanonicalChunk chunk : Arrays.asList(SEERS, LUMBRIDGE, GLARIALS_TOMB, OCEAN))
        {
            HudModel model = HudPresenter.present(facts(mid, chunk).mode(FateLockedConfig.HudMode.DETAILED)
                .bank(mid.nearestBank(chunk)).shop(mid.nearestShop(chunk)).run(run).build());
            for (HudModel.Line line : model.getLines())
            {
                String text = line.getLabel() + " " + line.getValue();
                assertFalse(text, text.matches(".*[✓✔✕✖✗○●⚠▶▸▼▾←→].*"));
            }
        }
    }

    @Test
    public void theWayToTheNearestBankIsACompassPoint()
    {
        assertEquals("E", HudPresenter.compass(5, 1));
        assertEquals("NE", HudPresenter.compass(5, 4));
        assertEquals("N", HudPresenter.compass(0, 3));
        assertEquals("SW", HudPresenter.compass(-2, -3));
        assertEquals("W", HudPresenter.compass(-4, 0));
        assertEquals("S", HudPresenter.compass(1, -2));
        assertEquals("exactly 2:1 is the cardinal", "E", HudPresenter.compass(4, 2));
        assertEquals("W", HudPresenter.compass(-2, 1));
    }

    /** A Compact HUD's facts for a player standing in this chunk. */
    static HudPresenter.Facts.FactsBuilder facts(DecisionService decisions, CanonicalChunk chunk)
    {
        return HudPresenter.Facts.builder().mode(FateLockedConfig.HudMode.COMPACT).decisions(decisions).chunk(chunk);
    }

    /** Each line's value by its label; a line with no value maps to null. */
    static Map<String, String> lines(HudModel model)
    {
        Map<String, String> lines = new LinkedHashMap<>();
        for (HudModel.Line line : model.getLines())
        {
            lines.put(line.getLabel(), line.getValue());
        }
        return lines;
    }

    private static List<String> labels(HudModel model)
    {
        List<String> labels = new ArrayList<>();
        for (HudModel.Line line : model.getLines())
        {
            labels.add(line.getLabel());
        }
        return labels;
    }

    private static HudModel.Line line(HudModel model, String label)
    {
        for (HudModel.Line line : model.getLines())
        {
            if (line.getLabel().equals(label)) return line;
        }
        throw new AssertionError("no " + label + " line");
    }

    /** The Status line's words and tone, and the Why line's, for a chunk. */
    private static List<String> status(DecisionService decisions, CanonicalChunk chunk)
    {
        HudModel model = HudPresenter.present(facts(decisions, chunk).build());
        HudModel.Line status = line(model, "Status");
        Map<String, String> lines = lines(model);
        return Arrays.asList(status.getValue(), String.valueOf(status.getTone()), lines.get("Why"));
    }

    private static String word(String entry)
    {
        switch (entry)
        {
            case "ALLOWED": return "Unlocked";
            case "LOCKED": return "Locked";
            case "NOT_READY": return "Not ready";
            default: return "Uncharted";
        }
    }

    private static String tone(String entry)
    {
        switch (entry)
        {
            case "ALLOWED": return "GOOD";
            case "LOCKED": return "BAD";
            case "NOT_READY": return "PENDING";
            default: return "NEUTRAL";
        }
    }

    private static DecisionService playing(String id, String player) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", player);
    }

    private static String fixtureText(String name) throws Exception
    {
        try (InputStream in = HudPresenterTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The rules as any character sees them: names aren't decisions. */
    private static DecisionService anyone(JsonObject root)
    {
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, root.toString())), null, null);
    }

    /** A snapshot's string field; null when absent, null or not a string. */
    private static String string(JsonObject snapshot, String field)
    {
        JsonElement value = snapshot.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
